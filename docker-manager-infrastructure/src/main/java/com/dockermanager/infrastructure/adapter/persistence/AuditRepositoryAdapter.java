package com.dockermanager.infrastructure.adapter.persistence;

import com.dockermanager.application.port.outbound.AuditRepositoryPort;
import com.dockermanager.domain.entity.AuditLog;
import com.dockermanager.domain.enums.AuditAction;
import com.dockermanager.infrastructure.adapter.persistence.repository.JpaAuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审计日志持久化适配器。实现应用层的 AuditRepositoryPort 出站端口。
 */
@Component
public class AuditRepositoryAdapter implements AuditRepositoryPort {

    private final JpaAuditLogRepository jpaAuditLogRepository;

    public AuditRepositoryAdapter(JpaAuditLogRepository jpaAuditLogRepository) {
        this.jpaAuditLogRepository = jpaAuditLogRepository;
    }

    @Override
    public AuditLog save(AuditLog auditLog) {
        return jpaAuditLogRepository.save(auditLog);
    }

    @Override
    public List<AuditLog> findRecent(int limit) {
        return jpaAuditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    public List<AuditLog> findByAction(AuditAction action, int limit) {
        return jpaAuditLogRepository.findByActionOrderByCreatedAtDesc(action, PageRequest.of(0, limit));
    }

    @Override
    public List<AuditLog> findByProjectName(String projectName, int limit) {
        return jpaAuditLogRepository.findByProjectNameOrderByCreatedAtDesc(projectName, PageRequest.of(0, limit));
    }
}
