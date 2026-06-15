package com.dockermanager.infrastructure.adapter.persistence;

import com.dockermanager.application.port.outbound.HostRepositoryPort;
import com.dockermanager.domain.entity.DockerHost;
import com.dockermanager.infrastructure.adapter.persistence.repository.JpaDockerHostRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/** 宿主机持久化适配器 */
@Component
public class HostRepositoryAdapter implements HostRepositoryPort {
    private final JpaDockerHostRepository repo;
    public HostRepositoryAdapter(JpaDockerHostRepository repo) { this.repo = repo; }
    @Override public List<DockerHost> findAll() { return repo.findAll(); }
    @Override public Optional<DockerHost> findById(Long id) { return repo.findById(id); }
    @Override public DockerHost save(DockerHost host) { return repo.save(host); }
    @Override public void deleteById(Long id) { repo.deleteById(id); }
    @Override public boolean existsById(Long id) { return repo.existsById(id); }
    @Override public List<DockerHost> findByEnabled(boolean enabled) { return repo.findByEnabled(enabled); }
}
