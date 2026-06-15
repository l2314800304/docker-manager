package com.dockermanager.infrastructure.adapter.persistence.repository;

import com.dockermanager.domain.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JpaAlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabled(boolean enabled);
}
