package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.application.port.outbound.StatusRecordRepositoryPort;
import com.dockermanager.domain.entity.StatusRecord;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StatusHistoryController {

    private final StatusRecordRepositoryPort statusRecordRepositoryPort;

    public StatusHistoryController(StatusRecordRepositoryPort statusRecordRepositoryPort) {
        this.statusRecordRepositoryPort = statusRecordRepositoryPort;
    }

    @GetMapping("/containers/{id}/history")
    public ResponseEntity<List<StatusRecord>> getContainerHistory(@PathVariable String id,
                                                                    @RequestParam(defaultValue = "50") int limit) {
        ParamValidator.requireContainerId(id, "无效的容器ID");
        limit = ParamValidator.normalizeLimit(limit, 200);
        return ResponseEntity.ok(statusRecordRepositoryPort.findByContainerId(id, limit));
    }

    @GetMapping("/projects/{projectName}/history")
    public ResponseEntity<List<StatusRecord>> getProjectHistory(@PathVariable String projectName,
                                                                  @RequestParam(defaultValue = "50") int limit) {
        ParamValidator.requireNotBlank(projectName, "项目名不能为空");
        limit = ParamValidator.normalizeLimit(limit, 200);
        return ResponseEntity.ok(statusRecordRepositoryPort.findByProjectName(projectName, limit));
    }
}
