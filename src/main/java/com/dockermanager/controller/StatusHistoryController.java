package com.dockermanager.controller;

import com.dockermanager.model.entity.StatusRecord;
import com.dockermanager.service.ContainerStateTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatusHistoryController {

    private final ContainerStateTracker stateTracker;

    public StatusHistoryController(ContainerStateTracker stateTracker) {
        this.stateTracker = stateTracker;
    }

    @GetMapping("/containers/{id}/history")
    public ResponseEntity<List<StatusRecord>> getContainerHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(stateTracker.getContainerHistory(id, Math.min(limit, 200)));
    }

    @GetMapping("/projects/{projectName}/history")
    public ResponseEntity<List<StatusRecord>> getProjectHistory(
            @PathVariable String projectName,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(stateTracker.getProjectHistory(projectName, Math.min(limit, 200)));
    }
}
