package com.dockermanager.controller;

import com.dockermanager.model.dto.ContainerStatsDTO;
import com.dockermanager.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/containers/{id}/stats")
    public ResponseEntity<ContainerStatsDTO> getStats(@PathVariable String id) {
        ContainerStatsDTO stats = statsService.getStatsSnapshot(id);
        if (stats == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/summary")
    public List<ContainerStatsDTO> getAllStats() {
        return statsService.getAllStats();
    }
}
