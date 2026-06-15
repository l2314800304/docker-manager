package com.dockermanager.application.service;

import com.dockermanager.application.port.outbound.HostMetricsPort;
import com.dockermanager.application.port.outbound.HostRepositoryPort;
import com.dockermanager.domain.dto.DockerHostDTO;
import com.dockermanager.domain.dto.HostMetricsDTO;
import com.dockermanager.domain.entity.DockerHost;
import com.dockermanager.domain.port.inbound.HostOperationPort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 宿主机管理应用服务。实现 HostOperationPort 入站端口。
 * 编排宿主机 CRUD、连接测试和指标采集。
 */
@Service
public class HostAppService implements HostOperationPort {

    private final HostRepositoryPort hostRepository;
    private final HostMetricsPort hostMetrics;

    public HostAppService(HostRepositoryPort hostRepository, HostMetricsPort hostMetrics) {
        this.hostRepository = hostRepository;
        this.hostMetrics = hostMetrics;
    }

    @Override
    public List<DockerHostDTO> listHosts() {
        return hostRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public Optional<DockerHostDTO> getHost(Long hostId) {
        return hostRepository.findById(hostId).map(this::toDTO);
    }

    @Override
    public DockerHostDTO addHost(String name, String connectionType, String connectionUrl,
                                  boolean tlsEnabled, String certPath) {
        DockerHost host = DockerHost.builder()
                .name(name).connectionType(connectionType).connectionUrl(connectionUrl)
                .tlsEnabled(tlsEnabled).certPath(certPath).enabled(true).status("UNKNOWN").build();
        return toDTO(hostRepository.save(host));
    }

    @Override
    public DockerHostDTO updateHost(Long hostId, String name, String connectionType,
                                     String connectionUrl, boolean tlsEnabled, String certPath, boolean enabled) {
        DockerHost host = hostRepository.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("宿主机不存在"));
        host.setName(name);
        host.setConnectionType(connectionType);
        host.setConnectionUrl(connectionUrl);
        host.setTlsEnabled(tlsEnabled);
        host.setCertPath(certPath);
        host.setEnabled(enabled);
        host.setStatus("UNKNOWN"); // 修改配置后需重新检测
        return toDTO(hostRepository.save(host));
    }

    @Override
    public void deleteHost(Long hostId) {
        if (!hostRepository.existsById(hostId)) throw new IllegalArgumentException("宿主机不存在");
        hostRepository.deleteById(hostId);
    }

    @Override
    public boolean testConnection(Long hostId) {
        DockerHost host = hostRepository.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("宿主机不存在"));
        try {
            String version = hostMetrics.testConnection(host);
            host.setStatus("ONLINE");
            host.setDockerVersion(version);
            host.setLastConnectedAt(LocalDateTime.now());
            hostRepository.save(host);
            return true;
        } catch (Exception e) {
            host.setStatus("OFFLINE");
            hostRepository.save(host);
            return false;
        }
    }

    @Override
    public HostMetricsDTO getHostMetrics(Long hostId) {
        DockerHost host = hostRepository.findById(hostId)
                .orElseThrow(() -> new IllegalArgumentException("宿主机不存在"));
        return hostMetrics.collectMetrics(host);
    }

    @Override
    public List<HostMetricsDTO> getAllHostMetrics() {
        return hostRepository.findByEnabled(true).stream()
                .map(host -> {
                    try { return hostMetrics.collectMetrics(host); }
                    catch (Exception e) { return null; }
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    @Override
    public void refreshHostStatus(Long hostId) {
        testConnection(hostId);
    }

    private DockerHostDTO toDTO(DockerHost h) {
        return DockerHostDTO.builder()
                .id(h.getId()).name(h.getName()).connectionType(h.getConnectionType())
                .connectionUrl(h.getConnectionUrl()).tlsEnabled(h.isTlsEnabled())
                .enabled(h.isEnabled()).status(h.getStatus()).dockerVersion(h.getDockerVersion())
                .lastConnectedAt(h.getLastConnectedAt()).createdAt(h.getCreatedAt()).build();
    }
}
