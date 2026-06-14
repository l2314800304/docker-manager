package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.application.port.outbound.DockerAdapterPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/containers/{id}/logs")
public class LogController {

    private final DockerAdapterPort dockerAdapterPort;

    public LogController(DockerAdapterPort dockerAdapterPort) {
        this.dockerAdapterPort = dockerAdapterPort;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getLogs(@PathVariable String id,
                                          @RequestParam(defaultValue = "200") int tail,
                                          @RequestParam(required = false) Integer since) {
        return ResponseEntity.ok(dockerAdapterPort.getHistoryLogs(id, tail, since));
    }
}
