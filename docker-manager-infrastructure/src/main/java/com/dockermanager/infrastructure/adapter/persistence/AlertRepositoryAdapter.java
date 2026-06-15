package com.dockermanager.infrastructure.adapter.persistence;

import com.dockermanager.application.port.outbound.AlertRepositoryPort;
import com.dockermanager.domain.entity.AlertRecord;
import com.dockermanager.domain.entity.AlertRule;
import com.dockermanager.infrastructure.adapter.persistence.repository.JpaAlertRecordRepository;
import com.dockermanager.infrastructure.adapter.persistence.repository.JpaAlertRuleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/** 告警持久化适配器 */
@Component
public class AlertRepositoryAdapter implements AlertRepositoryPort {
    private final JpaAlertRuleRepository ruleRepo;
    private final JpaAlertRecordRepository recordRepo;
    public AlertRepositoryAdapter(JpaAlertRuleRepository ruleRepo, JpaAlertRecordRepository recordRepo) {
        this.ruleRepo = ruleRepo; this.recordRepo = recordRepo;
    }
    @Override public List<AlertRule> findAllRules() { return ruleRepo.findAll(); }
    @Override public Optional<AlertRule> findRuleById(Long id) { return ruleRepo.findById(id); }
    @Override public AlertRule saveRule(AlertRule rule) { return ruleRepo.save(rule); }
    @Override public void deleteRuleById(Long id) { ruleRepo.deleteById(id); }
    @Override public List<AlertRule> findEnabledRules() { return ruleRepo.findByEnabled(true); }
    @Override public AlertRecord saveRecord(AlertRecord record) { return recordRepo.save(record); }
    @Override public List<AlertRecord> findRecentRecords(int limit) { return recordRepo.findAllByOrderByTriggeredAtDesc(PageRequest.of(0, limit)); }
    @Override public List<AlertRecord> findRecordsByRuleId(Long ruleId, int limit) { return recordRepo.findByRuleIdOrderByTriggeredAtDesc(ruleId, PageRequest.of(0, limit)); }
}
