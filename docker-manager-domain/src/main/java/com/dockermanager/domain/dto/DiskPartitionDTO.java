package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 磁盘分区信息 DTO。
 *
 * <p>表示宿主机上一个磁盘分区的存储使用情况。
 * 数据通过在容器内执行 {@code df -P}（POSIX 格式）命令采集，
 * 解析输出中的每一行得到各分区的文件系统名、挂载点、总量、已用、可用和使用率。</p>
 *
 * <h3>采集原理：</h3>
 * <ol>
 *   <li>在宿主机上任一运行中的容器内执行 {@code df -P --block-size=1}</li>
 *   <li>解析 POSIX 标准格式输出：
 *       <pre>
 *       Filesystem     1024-blocks      Used Available Capacity Mounted on
 *       /dev/sda1       103081868  51540936  51540932      50% /
 *       tmpfs             8153104         0   8153104       0% /dev
 *       </pre>
 *   </li>
 *   <li>过滤掉 tmpfs、devtmpfs 等虚拟文件系统，仅保留物理分区</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskPartitionDTO {

    /** 文件系统设备名（如 /dev/sda1、/dev/vda2） */
    private String filesystem;

    /** 挂载点路径（如 /、/home、/data） */
    private String mountPoint;

    /** 分区总容量（字节） */
    private long totalBytes;

    /** 已使用空间（字节） */
    private long usedBytes;

    /** 可用空间（字节） */
    private long availableBytes;

    /** 使用率百分比 (0-100) */
    private double usePercent;
}
