package com.dockermanager.docker;

import com.dockermanager.model.dto.ContainerStatsDTO;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 容器资源使用统计（Stats）采集器。
 *
 * <p>通过 Docker Stats API 获取容器的 CPU、内存、网络和磁盘 IO 实时数据。
 * 支持两种采集模式：</p>
 *
 * <h3>1. 流式采集（WebSocket 实时推送）：</h3>
 * <p>通过 {@link #startCollecting(String, Consumer)} 启动持续采集，
 * 每次收到新数据时通过回调推送给调用方（通常是 WebSocket Handler）。</p>
 *
 * <h3>2. 单次快照（REST API 查询）：</h3>
 * <p>通过 {@link #getStatsSnapshot(String)} 获取一次性的 Stats 快照数据。</p>
 *
 * <h3>CPU 百分比计算：</h3>
 * <p>Docker Stats API 返回的是累计 CPU 使用量（纳秒），需要两次采样计算差值：</p>
 * <pre>
 *   cpuPercent = (cpuDelta / systemDelta) × onlineCpus × 100
 *   其中:
 *     cpuDelta = 当前容器CPU累计值 - 上次容器CPU累计值
 *     systemDelta = 当前系统CPU累计值 - 上次系统CPU累计值
 * </pre>
 *
 * @see ContainerLogStreamer 日志流采集器（架构类似）
 * @see StatsWebSocketHandler WebSocket 推送处理器
 */
@Component
public class ContainerStatsCollector {

    private static final Logger log = LoggerFactory.getLogger(ContainerStatsCollector.class);
    private final DockerClientService dockerClientService;

    /** 活跃的采集器映射（collectorId → Closeable），用于管理和关闭采集器 */
    private final Map<String, Closeable> activeCollectors = new ConcurrentHashMap<>();

    /** 上一次采集的原始 Statistics 数据，用于计算 CPU/网络的增量值 */
    private final Map<String, Statistics> previousStats = new ConcurrentHashMap<>();

    public ContainerStatsCollector(DockerClientService dockerClientService) {
        this.dockerClientService = dockerClientService;
    }

    /**
     * 启动持续 Stats 采集（流式模式）。
     *
     * <p>创建一个异步的 Stats 流，每次 Docker daemon 推送新数据时
     * 将其转换为 {@link ContainerStatsDTO} 并通过回调通知调用方。</p>
     *
     * @param containerId 容器 ID
     * @param onStats     收到新数据时的回调函数
     * @return 采集器 ID（用于后续通过 {@link #stopCollector(String)} 关闭）
     */
    public String startCollecting(String containerId, Consumer<ContainerStatsDTO> onStats) {
        String collectorId = containerId + "-" + System.currentTimeMillis();

        ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Statistics stats) {
                if (stats != null) {
                    ContainerStatsDTO dto = toStatsDTO(containerId, stats);
                    onStats.accept(dto);
                    previousStats.put(containerId, stats);  // 保存用于下次增量计算
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Stats collection error for {}: {}", containerId, throwable.getMessage());
                activeCollectors.remove(collectorId);
            }

            @Override
            public void onComplete() {
                activeCollectors.remove(collectorId);
            }
        };

        // 执行 Stats 命令，返回可关闭的流连接
        Closeable closeable = dockerClientService.statsCmd(containerId).exec(callback);
        activeCollectors.put(collectorId, closeable);
        return collectorId;
    }

    /**
     * 获取单次 Stats 快照数据。
     *
     * <p>启动 Stats 流，接收一次数据后立即关闭。用于 REST API 的按需查询。</p>
     * <p>注意：首次快照时没有前一次数据，CPU 百分比和网络速率将为 0。</p>
     *
     * @param containerId 容器 ID
     * @return Stats DTO，获取失败时返回 null
     */
    public ContainerStatsDTO getStatsSnapshot(String containerId) {
        final Statistics[] result = new Statistics[1];

        try {
            dockerClientService.statsCmd(containerId).exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    result[0] = stats;
                }
            }).awaitCompletion();  // 阻塞等待一次数据
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (result[0] == null) return null;
        return toStatsDTO(containerId, result[0]);
    }

    /**
     * 停止指定的采集器。
     *
     * @param collectorId 采集器 ID（由 {@link #startCollecting} 返回）
     */
    public void stopCollector(String collectorId) {
        Closeable collector = activeCollectors.remove(collectorId);
        if (collector != null) {
            try {
                collector.close();
            } catch (IOException e) {
                log.warn("Failed to close stats collector {}: {}", collectorId, e.getMessage());
            }
        }
    }

    /**
     * 停止所有活跃的采集器（应用关闭时调用）。
     *
     * @see com.dockermanager.config.ResourceCleanupManager
     */
    public void stopAllCollectors() {
        activeCollectors.forEach((id, c) -> {
            try { c.close(); } catch (IOException e) { log.warn("Error closing collector", e); }
        });
        activeCollectors.clear();
        previousStats.clear();
    }

    /**
     * 将 Docker Statistics 原始数据转换为业务 DTO。
     *
     * <p>转换内容包括：</p>
     * <ul>
     *   <li><b>CPU</b>：基于前后两次采样计算使用百分比
     *       <br>公式：{@code (cpuDelta / systemDelta) × onlineCpus × 100}</li>
     *   <li><b>内存</b>：当前使用量 / 限制量 / 使用百分比</li>
     *   <li><b>网络</b>：累计收发字节数 + 每秒速率（基于两次采样差值）</li>
     *   <li><b>磁盘 IO</b>：Block IO 的读写字节数（从 blkio stats 聚合）</li>
     * </ul>
     *
     * @param containerId 容器 ID
     * @param stats       Docker API 返回的原始统计数据
     * @return 转换后的 DTO
     */
    private ContainerStatsDTO toStatsDTO(String containerId, Statistics stats) {
        // ===== CPU 使用率计算 =====
        // 需要前后两次采样的累计值差来计算实际百分比
        double cpuPercent = 0.0;
        Statistics prev = previousStats.get(containerId);
        if (prev != null && stats.getCpuStats() != null && prev.getCpuStats() != null) {
            long cpuDelta = getVal(stats.getCpuStats().getCpuUsage()) - getVal(prev.getCpuStats().getCpuUsage());
            long systemDelta = getSystemCpu(stats) - getSystemCpu(prev);
            int onlineCpus = getOnlineCpus(stats);
            if (systemDelta > 0 && cpuDelta > 0) {
                // Docker 官方 CPU 计算公式
                cpuPercent = ((double) cpuDelta / systemDelta) * onlineCpus * 100.0;
            }
        }

        int onlineCpus = stats.getCpuStats() != null && stats.getCpuStats().getOnlineCpus() != null
                ? stats.getCpuStats().getOnlineCpus() : 1;

        // ===== 内存统计 =====
        long memUsage = 0, memLimit = 1;
        double memPercent = 0.0;
        if (stats.getMemoryStats() != null) {
            memUsage = stats.getMemoryStats().getUsage() != null ? stats.getMemoryStats().getUsage() : 0;
            memLimit = stats.getMemoryStats().getLimit() != null ? stats.getMemoryStats().getLimit() : 1;
            memPercent = memLimit > 0 ? ((double) memUsage / memLimit) * 100.0 : 0.0;
        }

        // ===== 网络统计（聚合所有网络接口） =====
        long rxBytes = 0, txBytes = 0;
        if (stats.getNetworks() != null) {
            for (var entry : stats.getNetworks().values().stream().toList()) {
                if (entry != null) {
                    rxBytes += entry.getRxBytes() != null ? entry.getRxBytes() : 0;
                    txBytes += entry.getTxBytes() != null ? entry.getTxBytes() : 0;
                }
            }
        }

        // 计算网络速率（当前累计 - 上次累计 = 间隔内传输量）
        long prevRx = 0, prevTx = 0;
        if (prev != null && prev.getNetworks() != null) {
            for (var entry : prev.getNetworks().values().stream().toList()) {
                if (entry != null) {
                    prevRx += entry.getRxBytes() != null ? entry.getRxBytes() : 0;
                    prevTx += entry.getTxBytes() != null ? entry.getTxBytes() : 0;
                }
            }
        }

        // ===== 磁盘 IO 统计（聚合 Read/Write 操作） =====
        long readBytes = 0, writeBytes = 0;
        if (stats.getBlkioStats() != null && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
            for (var entry : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                if (entry != null && entry.getOp() != null) {
                    if ("Read".equalsIgnoreCase(entry.getOp())) readBytes += entry.getValue() != null ? entry.getValue() : 0;
                    if ("Write".equalsIgnoreCase(entry.getOp())) writeBytes += entry.getValue() != null ? entry.getValue() : 0;
                }
            }
        }

        // 组装 DTO，百分比值保留两位小数
        return ContainerStatsDTO.builder()
                .containerId(containerId)
                .timestamp(LocalDateTime.now())
                .cpu(ContainerStatsDTO.CpuStats.builder()
                        .percent(Math.round(cpuPercent * 100.0) / 100.0)
                        .totalUsage(stats.getCpuStats() != null && stats.getCpuStats().getCpuUsage() != null
                                ? stats.getCpuStats().getCpuUsage().getTotalUsage() != null
                                ? stats.getCpuStats().getCpuUsage().getTotalUsage() : 0 : 0)
                        .systemUsage(getSystemCpu(stats))
                        .onlineCpus(onlineCpus)
                        .build())
                .memory(ContainerStatsDTO.MemoryStats.builder()
                        .usage(memUsage)
                        .limit(memLimit)
                        .percent(Math.round(memPercent * 100.0) / 100.0)
                        .build())
                .network(ContainerStatsDTO.NetworkStats.builder()
                        .rxBytes(rxBytes)
                        .txBytes(txBytes)
                        .rxBytesPerSec(rxBytes - prevRx)  // 间隔内的接收字节数（近似 bytes/sec）
                        .txBytesPerSec(txBytes - prevTx)  // 间隔内的发送字节数
                        .build())
                .blockIO(ContainerStatsDTO.BlockIOStats.builder()
                        .readBytes(readBytes)
                        .writeBytes(writeBytes)
                        .build())
                .build();
    }

    /** 安全获取 CPU 累计使用量（纳秒） */
    private long getVal(com.github.dockerjava.api.model.CpuUsageConfig usage) {
        if (usage == null || usage.getTotalUsage() == null) return 0;
        return usage.getTotalUsage();
    }

    /** 安全获取系统 CPU 累计使用量 */
    private long getSystemCpu(Statistics stats) {
        if (stats.getCpuStats() != null && stats.getCpuStats().getSystemCpuUsage() != null) {
            return stats.getCpuStats().getSystemCpuUsage();
        }
        return 0;
    }

    /** 安全获取在线 CPU 核心数 */
    private int getOnlineCpus(Statistics stats) {
        if (stats.getCpuStats() != null && stats.getCpuStats().getOnlineCpus() != null) {
            return stats.getCpuStats().getOnlineCpus();
        }
        return 1;
    }
}
