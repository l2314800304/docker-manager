package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.entity.AlertRecord;
import com.dockermanager.domain.entity.AlertRule;
import java.util.List;
import java.util.Optional;

/** 告警持久化出站端口 */
public interface AlertRepositoryPort {
    // Rules
    List<AlertRule> findAllRules();
    Optional<AlertRule> findRuleById(Long id);
    AlertRule saveRule(AlertRule rule);
    void deleteRuleById(Long id);
    List<AlertRule> findEnabledRules();
    // Records
    AlertRecord saveRecord(AlertRecord record);
    List<AlertRecord> findRecentRecords(int limit);
    List<AlertRecord> findRecordsByRuleId(Long ruleId, int limit);
}
