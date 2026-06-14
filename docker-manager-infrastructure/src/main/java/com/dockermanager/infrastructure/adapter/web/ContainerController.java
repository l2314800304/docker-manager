package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ContainerInfoDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final DockerOperationPort dockerOperationPort;

    public ContainerController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping
    public List<ContainerInfoDTO> getAllContainers() {
        return dockerOperationPort.getAllContainers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContainerInfoDTO> getContainer(@PathVariable String id) {
        return dockerOperationPort.getContainer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectContainer(@PathVariable String id) {
        try { return ResponseEntity.ok(dockerOperationPort.inspectContainer(id)); }
        catch (Exception e) { return ResponseEntity.notFound().build(); }
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, Object>> restartContainer(@PathVariable String id) {
        try {
            dockerOperationPort.restartContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container restarting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopContainer(@PathVariable String id) {
        try {
            dockerOperationPort.stopContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container stopping"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startContainer(@PathVariable String id) {
        try {
            dockerOperationPort.startContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container starting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
