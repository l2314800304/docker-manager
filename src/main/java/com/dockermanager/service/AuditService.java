package com.dockermanager.service;

import com.dockermanager.model.entity.AuditLog;
import com.dockermanager.model.enums.AuditAction;
import com.dockermanager.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(AuditAction action, String containerId, String projectName,
                    String serviceName, String result, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .containerId(containerId)
                .projectName(projectName)
                .serviceName(serviceName)
                .result(result)
                .detail(detail)
                .build();
        auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
