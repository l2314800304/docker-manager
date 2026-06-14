package com.dockermanager.controller;

import com.dockermanager.docker.ContainerLifecycleManager;
import com.dockermanager.model.dto.ImageUpdateRequest;
import com.dockermanager.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/images/{imageName}/tags")
    public List<String> getImageTags(@PathVariable String imageName) {
        return imageService.getImageTags(imageName);
    }

    @PostMapping("/projects/{projectName}/services/{serviceName}/update")
    public ResponseEntity<Map<String, String>> updateService(
            @PathVariable String projectName,
            @PathVariable String serviceName,
            @RequestBody ImageUpdateRequest request) {

        String taskId = imageService.updateServiceImage(projectName, serviceName, request,
                progress -> { /* progress callback */ });

        return ResponseEntity.accepted().body(Map.of(
                "taskId", taskId,
                "message", "Update started"
        ));
    }

    @GetMapping("/tasks/{taskId}/status")
    public ResponseEntity<Map<String, String>> getTaskStatus(@PathVariable String taskId) {
        ContainerLifecycleManager.TaskStatus status = imageService.getTaskStatus(taskId);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "status", status.status(),
                "message", status.message()
        ));
    }
}
