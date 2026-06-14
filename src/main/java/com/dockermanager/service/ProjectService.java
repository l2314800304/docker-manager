package com.dockermanager.service;

import com.dockermanager.docker.ComposeProjectDiscovery;
import com.dockermanager.model.dto.ComposeProjectDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ComposeProjectDiscovery discovery;

    public ProjectService(ComposeProjectDiscovery discovery) {
        this.discovery = discovery;
    }

    public List<ComposeProjectDTO> getAllProjects() {
        return discovery.discoverProjects();
    }

    public Optional<ComposeProjectDTO> getProject(String projectName) {
        return discovery.getProject(projectName);
    }
}
