package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.entity.DockerHost;
import java.util.List;
import java.util.Optional;

/** 宿主机持久化出站端口 */
public interface HostRepositoryPort {
    List<DockerHost> findAll();
    Optional<DockerHost> findById(Long id);
    DockerHost save(DockerHost host);
    void deleteById(Long id);
    boolean existsById(Long id);
    List<DockerHost> findByEnabled(boolean enabled);
}
