package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.entity.DockerHost;

/**
 * 宿主机指标采集出站端口。由 Infrastructure 层的 HostMetricsCollector 实现。
 */
public interface HostMetricsPort {
    /** 采集指定宿主机的资源指标 */
    HostMetricsDTO collectMetrics(DockerHost host);
    /** 测试宿主机连接，返回 Docker 版本号 */
    String testConnection(DockerHost host);
}
