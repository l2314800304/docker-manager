package com.dockermanager.infrastructure.adapter.persistence.repository;

import com.dockermanager.domain.entity.StatusRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA 状态记录 Repository */
@Repository
public interface JpaStatusRecordRepository extends JpaRepository<StatusRecord, Long> {
    List<StatusRecord> findByContainerIdOrderByRecordedAtDesc(String containerId, PageRequest pageable);
    List<StatusRecord> findByProjectNameOrderByRecordedAtDesc(String projectName, PageRequest pageable);
}
