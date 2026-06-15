package com.dockermanager.infrastructure.adapter.persistence.repository;

import com.dockermanager.domain.entity.AlertRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JpaAlertRecordRepository extends JpaRepository<AlertRecord, Long> {
    List<AlertRecord> findAllByOrderByTriggeredAtDesc(PageRequest pageable);
    List<AlertRecord> findByRuleIdOrderByTriggeredAtDesc(Long ruleId, PageRequest pageable);
}
