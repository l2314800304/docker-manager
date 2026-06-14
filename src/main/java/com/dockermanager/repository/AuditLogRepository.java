package com.dockermanager.repository;

import com.dockermanager.model.entity.AuditLog;
import com.dockermanager.model.enums.AuditAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);
    List<AuditLog> findByProjectNameOrderByCreatedAtDesc(String projectName, Pageable pageable);
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
