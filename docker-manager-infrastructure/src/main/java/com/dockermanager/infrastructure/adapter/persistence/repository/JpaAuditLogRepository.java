package com.dockermanager.infrastructure.adapter.persistence.repository;

import com.dockermanager.domain.entity.AuditLog;
import com.dockermanager.domain.enums.AuditAction;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA 审计日志 Repository */
@Repository
public interface JpaAuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, PageRequest pageable);
    List<AuditLog> findByProjectNameOrderByCreatedAtDesc(String projectName, PageRequest pageable);
    List<AuditLog> findAllByOrderByCreatedAtDesc(PageRequest pageable);
}
