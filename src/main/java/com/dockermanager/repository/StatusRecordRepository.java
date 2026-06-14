package com.dockermanager.repository;

import com.dockermanager.model.entity.StatusRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StatusRecordRepository extends JpaRepository<StatusRecord, Long> {
    List<StatusRecord> findByContainerIdOrderByRecordedAtDesc(String containerId, Pageable pageable);
    List<StatusRecord> findByProjectNameOrderByRecordedAtDesc(String projectName, Pageable pageable);
    List<StatusRecord> findByRecordedAtBetweenOrderByRecordedAtDesc(LocalDateTime from, LocalDateTime to);
}
