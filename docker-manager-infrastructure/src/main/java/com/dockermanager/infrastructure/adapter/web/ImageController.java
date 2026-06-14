package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ImageUpdateRequest;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImageController {

    private final DockerOperationPort dockerOperationPort;

    public ImageController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping("/images/{imageName}/tags")
    public List<String> getImageTags(@PathVariable String imageName) {
        return dockerOperationPort.getImageTags(imageName);
    }

    @PostMapping("/projects/{projectName}/services/{serviceName}/update")
    public ResponseEntity<Map<String, String>> updateService(
            @PathVariable String projectName, @PathVariable String serviceName,
            @RequestBody ImageUpdateRequest request) {
        String taskId = dockerOperationPort.updateServiceImage(projectName, serviceName, request);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId, "message", "Update started"));
    }

    @GetMapping("/tasks/{taskId}/status")
    public ResponseEntity<Map<String, String>> getTaskStatus(@PathVariable String taskId) {
        Map<String, String> status = dockerOperationPort.getTaskStatus(taskId);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(status);
    }
}
