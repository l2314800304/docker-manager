package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ComposeProjectDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final DockerOperationPort dockerOperationPort;

    public ProjectController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping
    public List<ComposeProjectDTO> getAllProjects() {
        return dockerOperationPort.discoverProjects();
    }

    @GetMapping("/{projectName}")
    public ResponseEntity<ComposeProjectDTO> getProject(@PathVariable String projectName) {
        return dockerOperationPort.getProject(projectName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{projectName}/refresh")
    public ResponseEntity<ComposeProjectDTO> refreshProject(@PathVariable String projectName) {
        return dockerOperationPort.getProject(projectName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
