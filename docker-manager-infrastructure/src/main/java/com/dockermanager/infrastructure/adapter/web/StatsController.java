package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.dto.ContainerStatsDTO;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final DockerOperationPort dockerOperationPort;

    public StatsController(DockerOperationPort dockerOperationPort) {
        this.dockerOperationPort = dockerOperationPort;
    }

    @GetMapping("/containers/{id}/stats")
    public ResponseEntity<ContainerStatsDTO> getStats(@PathVariable String id) {
        ContainerStatsDTO stats = dockerOperationPort.getStatsSnapshot(id);
        if (stats == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/summary")
    public List<ContainerStatsDTO> getAllStats() {
        return dockerOperationPort.getAllStats();
    }
}
