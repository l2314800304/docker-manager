package com.dockermanager.controller;

import com.dockermanager.service.LogService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/containers/{id}/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "200") int tail,
            @RequestParam(required = false) Integer since) {
        String logs = logService.getLogs(id, tail, since);
        return ResponseEntity.ok(logs);
    }
}
