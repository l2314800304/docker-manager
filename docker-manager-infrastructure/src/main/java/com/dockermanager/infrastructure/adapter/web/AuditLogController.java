package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.application.port.outbound.AuditRepositoryPort;
import com.dockermanager.domain.entity.AuditLog;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditRepositoryPort auditRepositoryPort;

    public AuditLogController(AuditRepositoryPort auditRepositoryPort) {
        this.auditRepositoryPort = auditRepositoryPort;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getRecentLogs(@RequestParam(defaultValue = "50") int limit) {
        limit = ParamValidator.normalizeLimit(limit, 200);
        return ResponseEntity.ok(auditRepositoryPort.findRecent(limit));
    }
}
