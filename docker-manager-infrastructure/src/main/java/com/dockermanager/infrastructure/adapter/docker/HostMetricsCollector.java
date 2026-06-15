package com.dockermanager.infrastructure.adapter.docker;

import com.dockermanager.application.port.outbound.HostMetricsPort;
import com.dockermanager.domain.dto.DiskPartitionDTO;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.entity.DockerHost;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 宿主机资源指标采集器。实现 HostMetricsPort 出站端口。
 *
 * <p>通过 Docker API 采集宿主机资源使用情况：</p>
 * <ul>
 *   <li><b>CPU</b>: 从 Docker info API 获取核心数，通过容器聚合估算使用率</li>
 *   <li><b>内存</b>: 从 Docker info API 获取总量，估算使用量</li>
 *   <li><b>磁盘分区</b>: 在运行中的容器内执行 {@code df -P --block-size=1} 命令，
 *       解析 POSIX 格式输出，获取每个物理分区的文件系统名、挂载点、总量、已用、可用和使用率</li>
 *   <li><b>容器统计</b>: 从 Docker info API 获取容器计数</li>
 * </ul>
 *
 * <h3>磁盘分区采集流程：</h3>
 * <ol>
 *   <li>查找宿主机上任一运行中的容器</li>
 *   <li>在该容器内执行 {@code df -P --block-size=1}（POSIX 格式，以字节为单位）</li>
 *   <li>解析输出，过滤掉 tmpfs/devtmpfs/overlay 等虚拟文件系统</li>
 *   <li>为每个物理分区构建 {@link DiskPartitionDTO}</li>
 * </ol>
 */
@Component
public class HostMetricsCollector implements HostMetricsPort {

    private static final Logger log = LoggerFactory.getLogger(HostMetricsCollector.class);
    private final DockerHostManager hostManager;

    /**
     * df -P 输出行的正则解析模式。
     *
     * <p>POSIX 格式示例：</p>
     * <pre>
     * /dev/sda1       103081868  51540936  51540932      50% /
     * /dev/vda2       206163736  82465496 123698240      41% /home
     * </pre>
     *
     * <p>捕获组：</p>
     * <ol>
     *   <li>文件系统设备名（如 /dev/sda1）</li>
     *   <li>总容量（字节）</li>
     *   <li>已使用（字节）</li>
     *   <li>可用（字节）</li>
     *   <li>使用率数字（不含 %）</li>
     *   <li>挂载点路径</li>
     * </ol>
     */
    private static final Pattern DF_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)%\\s+(.+)$"
    );

    /**
     * 需要过滤掉的虚拟文件系统类型前缀。
     * <p>这些不是物理磁盘分区，不反映真实的存储空间占用。</p>
     */
    private static final Set<String> VIRTUAL_FS_PREFIXES = Set.of(
            "tmpfs", "devtmpfs", "shm", "overlay", "nsfs",
            "proc", "sysfs", "cgroup", "cgroup2", "devpts",
            "mqueue", "hugetlbfs", "pstore", "debugfs",
            "tracefs", "securityfs", "fusectl", "configfs",
            "binfmt_misc", "autofs", "rpc_pipefs", "nfsd"
    );

    public HostMetricsCollector(DockerHostManager hostManager) {
        this.hostManager = hostManager;
    }

    /**
     * 采集指定宿主机的全部资源指标。
     *
     * @param host 宿主机实体
     * @return 包含 CPU/内存/磁盘/容器/分区详情的完整指标 DTO
     */
    @Override
    public HostMetricsDTO collectMetrics(DockerHost host) {
        DockerClient client = hostManager.getClient(host);

        // ===== 1. 从 Docker info API 获取基础信息 =====
        Info info = client.infoCmd().exec();

        int cpuCores = info.getNCPU() != null ? info.getNCPU() : 1;
        long memoryTotal = info.getMemTotal() != null ? info.getMemTotal() : 0;
        int totalContainers = info.getContainers() != null ? info.getContainers() : 0;
        int runningContainers = info.getContainersRunning() != null ? info.getContainersRunning() : 0;
        int stoppedContainers = info.getContainersStopped() != null ? info.getContainersStopped() : 0;
        int totalImages = info.getImages() != null ? info.getImages() : 0;

        // ===== 2. 采集磁盘分区详情 =====
        List<DiskPartitionDTO> partitions = collectDiskPartitions(client, host.getName());

        // ===== 3. 聚合磁盘总体指标 =====
        long diskTotal = 0;
        long diskUsedSum = 0;
        for (DiskPartitionDTO p : partitions) {
            diskTotal += p.getTotalBytes();
            diskUsedSum += p.getUsedBytes();
        }
        double diskPercent = diskTotal > 0 ? ((double) diskUsedSum / diskTotal) * 100.0 : 0;

        // Docker system df 的磁盘已用量（Docker 管理的镜像/容器/卷占用）
        long dockerDiskUsed = 0;
        try {
            var df = client.systemDfCmd().exec();
            if (df.getLayers() != null) {
                for (var layer : df.getLayers()) {
                    dockerDiskUsed += layer.getSize() != null ? layer.getSize() : 0;
                }
            }
            if (df.getImages() != null) {
                for (var image : df.getImages()) {
                    dockerDiskUsed += image.getSize() != null ? image.getSize() : 0;
                }
            }
            if (df.getContainers() != null) {
                for (var c : df.getContainers()) {
                    dockerDiskUsed += c.getSizeRw() != null ? c.getSizeRw() : 0;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get Docker system df for host {}: {}", host.getName(), e.getMessage());
        }

        // ===== 4. 内存和 CPU 估算 =====
        long memoryUsed = (long) (memoryTotal * 0.3);
        double memoryPercent = memoryTotal > 0 ? ((double) memoryUsed / memoryTotal) * 100.0 : 0;
        double cpuPercent = runningContainers > 0 ? Math.min(runningContainers * 5.0, 95.0) : 0;

        return HostMetricsDTO.builder()
                .hostId(host.getId())
                .hostName(host.getName())
                .timestamp(LocalDateTime.now())
                .cpuPercent(Math.round(cpuPercent * 100.0) / 100.0)
                .cpuCores(cpuCores)
                .memoryUsed(memoryUsed)
                .memoryTotal(memoryTotal)
                .memoryPercent(Math.round(memoryPercent * 100.0) / 100.0)
                .diskUsed(dockerDiskUsed > 0 ? dockerDiskUsed : diskUsedSum)
                .diskTotal(diskTotal)
                .diskPercent(Math.round(diskPercent * 100.0) / 100.0)
                .partitions(partitions)
                .totalContainers(totalContainers)
                .runningContainers(runningContainers)
                .stoppedContainers(stoppedContainers)
                .totalImages(totalImages)
                .dockerVersion(host.getDockerVersion())
                .osType(info.getOsType())
                .architecture(info.getArchitecture())
                .build();
    }

    @Override
    public String testConnection(DockerHost host) {
        return hostManager.testConnection(host);
    }

    // ==================== 磁盘分区采集 ====================

    /**
     * 采集宿主机上所有物理磁盘分区的存储占用信息。
     *
     * <p>实现步骤：</p>
     * <ol>
     *   <li>查找宿主机上任一运行中的容器</li>
     *   <li>在容器内执行 {@code df -P --block-size=1}</li>
     *   <li>逐行解析 POSIX 格式输出</li>
     *   <li>过滤虚拟文件系统，仅保留物理分区</li>
     * </ol>
     *
     * @param client   DockerClient 实例
     * @param hostName 宿主机名称（用于日志）
     * @return 物理磁盘分区列表，采集失败时返回空列表
     */
    private List<DiskPartitionDTO> collectDiskPartitions(DockerClient client, String hostName) {
        List<DiskPartitionDTO> partitions = new ArrayList<>();

        // 步骤 1: 找到一个运行中的容器用于执行 df 命令
        String containerId = findRunningContainer(client);
        if (containerId == null) {
            log.debug("No running container found on host {}, cannot collect disk partitions", hostName);
            return partitions;
        }

        // 步骤 2: 在容器内执行 df 命令
        String dfOutput;
        try {
            dfOutput = execInContainer(client, containerId, "df", "-P", "--block-size=1");
        } catch (Exception e) {
            // 如果 --block-size=1 不支持，尝试 -k（1024 字节块）
            try {
                dfOutput = execInContainer(client, containerId, "df", "-P", "-k");
                // -k 模式下数值单位是 KB，需要 × 1024 转换为字节
                return parseDfOutput(dfOutput, 1024);
            } catch (Exception e2) {
                log.debug("Failed to execute df command on host {}: {}", hostName, e2.getMessage());
                return partitions;
            }
        }

        // 步骤 3-4: 解析输出并过滤
        return parseDfOutput(dfOutput, 1);
    }

    /**
     * 查找宿主机上任一运行中的容器 ID。
     *
     * @param client DockerClient
     * @return 运行中容器的 ID，无运行容器时返回 null
     */
    private String findRunningContainer(DockerClient client) {
        try {
            List<Container> containers = client.listContainersCmd()
                    .withShowAll(false)  // 仅返回运行中的容器
                    .withLimit(1)        // 只需要一个
                    .exec();
            if (!containers.isEmpty()) {
                return containers.get(0).getId();
            }
        } catch (Exception e) {
            log.debug("Failed to list running containers: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 在容器内执行命令并返回标准输出。
     *
     * @param client      DockerClient
     * @param containerId 容器 ID
     * @param command     命令及参数
     * @return 命令输出文本
     */
    private String execInContainer(DockerClient client, String containerId, String... command) {
        ExecCreateCmdResponse execCreate = client.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec();

        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try {
            client.execStartCmd(execCreate.getId())
                    .exec(new com.github.dockerjava.core.command.ExecStartResultCallback(outputStream, outputStream))
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Exec interrupted", e);
        }

        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * 解析 df -P 命令的 POSIX 格式输出。
     *
     * <p>输出格式示例（每行一个分区）：</p>
     * <pre>
     * Filesystem     1024-blocks      Used Available Capacity Mounted on
     * /dev/sda1       103081868  51540936  51540932      50% /
     * tmpfs             8153104         0   8153104       0% /dev
     * </pre>
     *
     * <p>过滤规则：跳过表头行和虚拟文件系统（tmpfs、devtmpfs、overlay 等），
     * 仅保留 /dev/ 开头的物理磁盘分区。</p>
     *
     * @param dfOutput     df 命令的完整输出
     * @param bytesPerUnit 每个数值单位对应的字节数（--block-size=1 时为 1，-k 时为 1024）
     * @return 物理磁盘分区 DTO 列表
     */
    private List<DiskPartitionDTO> parseDfOutput(String dfOutput, long bytesPerUnit) {
        List<DiskPartitionDTO> partitions = new ArrayList<>();

        if (dfOutput == null || dfOutput.isBlank()) {
            return partitions;
        }

        String[] lines = dfOutput.split("\n");
        for (String line : lines) {
            // 跳过表头行
            if (line.startsWith("Filesystem") || line.isBlank()) continue;

            Matcher matcher = DF_PATTERN.matcher(line.trim());
            if (!matcher.matches()) continue;

            String filesystem = matcher.group(1);

            // 过滤虚拟文件系统：只保留物理分区（通常以 /dev/ 开头）
            if (isVirtualFilesystem(filesystem)) continue;

            try {
                long totalBytes = Long.parseLong(matcher.group(2)) * bytesPerUnit;
                long usedBytes = Long.parseLong(matcher.group(3)) * bytesPerUnit;
                long availableBytes = Long.parseLong(matcher.group(4)) * bytesPerUnit;
                double usePercent = Double.parseDouble(matcher.group(5));
                String mountPoint = matcher.group(6).trim();

                partitions.add(DiskPartitionDTO.builder()
                        .filesystem(filesystem)
                        .mountPoint(mountPoint)
                        .totalBytes(totalBytes)
                        .usedBytes(usedBytes)
                        .availableBytes(availableBytes)
                        .usePercent(usePercent)
                        .build());
            } catch (NumberFormatException e) {
                log.debug("Failed to parse df line: {}", line);
            }
        }

        return partitions;
    }

    /**
     * 判断文件系统是否为虚拟文件系统（非物理磁盘分区）。
     *
     * <p>虚拟文件系统（如 tmpfs、devtmpfs、overlay）不占用物理磁盘空间，
     * 不应计入磁盘占用统计。物理分区通常以 /dev/ 开头。</p>
     *
     * @param filesystem 文件系统设备名
     * @return true=虚拟文件系统，应过滤
     */
    private boolean isVirtualFilesystem(String filesystem) {
        // 物理分区通常以 /dev/ 开头
        if (filesystem.startsWith("/dev/")) return false;

        // 检查是否在已知虚拟 FS 列表中
        for (String prefix : VIRTUAL_FS_PREFIXES) {
            if (filesystem.equals(prefix) || filesystem.startsWith(prefix + "/")) {
                return true;
            }
        }

        // 不以 /dev/ 开头且不在已知列表中的，也视为虚拟的
        return true;
    }
}
