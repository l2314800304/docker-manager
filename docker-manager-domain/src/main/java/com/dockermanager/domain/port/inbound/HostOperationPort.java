package com.dockermanager.domain.port.inbound;

import com.dockermanager.domain.dto.DockerHostDTO;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.entity.DockerHost;
import java.util.List;
import java.util.Optional;

/**
 * 宿主机操作入站端口。定义宿主机管理和指标采集的业务契约。
 */
public interface HostOperationPort {
    /** 获取所有宿主机列表 */
    List<DockerHostDTO> listHosts();
    /** 获取宿主机详情 */
    Optional<DockerHostDTO> getHost(Long hostId);
    /** 添加宿主机 */
    DockerHostDTO addHost(String name, String connectionType, String connectionUrl, boolean tlsEnabled, String certPath);
    /** 更新宿主机 */
    DockerHostDTO updateHost(Long hostId, String name, String connectionType, String connectionUrl, boolean tlsEnabled, String certPath, boolean enabled);
    /** 删除宿主机 */
    void deleteHost(Long hostId);
    /** 测试宿主机连接 */
    boolean testConnection(Long hostId);
    /** 获取宿主机资源指标 */
    HostMetricsDTO getHostMetrics(Long hostId);
    /** 获取所有宿主机的资源指标 */
    List<HostMetricsDTO> getAllHostMetrics();
    /** 刷新宿主机连接状态 */
    void refreshHostStatus(Long hostId);
}
