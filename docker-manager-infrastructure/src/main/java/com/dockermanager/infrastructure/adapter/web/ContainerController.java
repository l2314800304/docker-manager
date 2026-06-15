package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ContainerInfoDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import com.dockermanager.domain.util.ParamValidator;
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
        ParamValidator.requireContainerId(id, "无效的容器ID");
        return dockerOperationPort.getContainer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectContainer(@PathVariable String id) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        try { return ResponseEntity.ok(dockerOperationPort.inspectContainer(id)); }
        catch (Exception e) { return ResponseEntity.notFound().build(); }
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, Object>> restartContainer(@PathVariable String id) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        try {
            dockerOperationPort.restartContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container restarting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopContainer(@PathVariable String id) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        try {
            dockerOperationPort.stopContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container stopping"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startContainer(@PathVariable String id) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        try {
            dockerOperationPort.startContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container starting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
