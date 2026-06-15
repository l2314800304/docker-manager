package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 宿主机资源指标 DTO。
 *
 * <p>包含 CPU、内存、磁盘、Docker 引擎等实时指标，以及各磁盘分区的详细占用信息。</p>
 *
 * <h3>数据来源：</h3>
 * <ul>
 *   <li><b>CPU/内存/容器数</b>: Docker API 的 {@code /info} 端点</li>
 *   <li><b>Docker 磁盘占用</b>: Docker API 的 {@code /system/df} 端点</li>
 *   <li><b>磁盘分区详情</b>: 在容器内执行 {@code df -P --block-size=1} 命令采集</li>
 * </ul>
 *
 * @see DiskPartitionDTO 单个磁盘分区的详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostMetricsDTO {

    /** 宿主机 ID */
    private Long hostId;

    /** 宿主机名称 */
    private String hostName;

    /** 采集时间 */
    private LocalDateTime timestamp;

    // ==================== CPU 指标 ====================

    /** CPU 使用率 (0-100%) */
    private double cpuPercent;

    /** CPU 核心数 */
    private int cpuCores;

    // ==================== 内存指标 ====================

    /** 内存已使用量 (bytes) */
    private long memoryUsed;

    /** 内存总量 (bytes) */
    private long memoryTotal;

    /** 内存使用率 (0-100%) */
    private double memoryPercent;

    // ==================== 磁盘聚合指标 ====================

    /** Docker 管理的磁盘已使用量 (bytes)，来自 system df */
    private long diskUsed;

    /** 磁盘总量 (bytes)，取所有物理分区的总量之和 */
    private long diskTotal;

    /** 磁盘使用率 (0-100%)，所有物理分区的加权平均 */
    private double diskPercent;

    // ==================== 磁盘分区详情 ====================

    /**
     * 各磁盘分区的存储占用详情。
     * <p>包含宿主机上所有物理磁盘分区（过滤掉 tmpfs/devtmpfs 等虚拟文件系统），
     * 每个分区提供文件系统名、挂载点、总量、已用、可用和使用率。</p>
     */
    private List<DiskPartitionDTO> partitions;

    // ==================== 容器与镜像统计 ====================

    /** 容器总数 */
    private int totalContainers;

    /** 运行中容器数 */
    private int runningContainers;

    /** 已停止容器数 */
    private int stoppedContainers;

    /** 镜像总数 */
    private int totalImages;

    // ==================== Docker 引擎信息 ====================

    /** Docker 版本号 */
    private String dockerVersion;

    /** 操作系统类型 */
    private String osType;

    /** 系统架构 */
    private String architecture;
}
