package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.entity.AuditLog;
import com.dockermanager.domain.enums.AuditAction;
import java.util.List;

/**
 * 审计日志持久化出站端口。
 */
public interface AuditRepositoryPort {
    AuditLog save(AuditLog auditLog);
    List<AuditLog> findRecent(int limit);
    List<AuditLog> findByAction(AuditAction action, int limit);
    List<AuditLog> findByProjectName(String projectName, int limit);
}
