package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.application.port.outbound.DockerAdapterPort;
import com.dockermanager.domain.util.ParamValidator;
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
        ParamValidator.requireContainerId(id, "无效的容器ID");
        tail = ParamValidator.normalizeLimit(tail, 5000);
        return ResponseEntity.ok(dockerAdapterPort.getHistoryLogs(id, tail, since));
    }
}
