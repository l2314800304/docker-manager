package com.dockermanager.controller;

import com.dockermanager.model.dto.ComposeProjectDTO;
import com.dockermanager.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ComposeProjectDTO> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{projectName}")
    public ResponseEntity<ComposeProjectDTO> getProject(@PathVariable String projectName) {
        return projectService.getProject(projectName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{projectName}/refresh")
    public ResponseEntity<ComposeProjectDTO> refreshProject(@PathVariable String projectName) {
        return projectService.getProject(projectName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
