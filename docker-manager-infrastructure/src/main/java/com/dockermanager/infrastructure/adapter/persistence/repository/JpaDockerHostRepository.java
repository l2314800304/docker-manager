package com.dockermanager.infrastructure.adapter.persistence.repository;

import com.dockermanager.domain.entity.DockerHost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JpaDockerHostRepository extends JpaRepository<DockerHost, Long> {
    List<DockerHost> findByEnabled(boolean enabled);
}
