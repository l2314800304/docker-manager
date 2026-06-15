package com.dockermanager.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 告警记录实体。记录每次触发的告警事件。 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "alert_record")
public class AlertRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "rule_id", nullable = false) private Long ruleId;
    @Column(name = "rule_name", length = 100) private String ruleName;
    @Column(name = "host_id") private Long hostId;
    @Column(name = "host_name", length = 100) private String hostName;
    @Column(name = "metric_type", nullable = false, length = 50) private String metricType;
    @Column(name = "metric_value", nullable = false) private double metricValue;
    @Column(nullable = false) private double threshold;
    @Column(columnDefinition = "TEXT") private String message;
    @Column(name = "notify_status", length = 20) private String notifyStatus;
    @Column(name = "notify_result", columnDefinition = "TEXT") private String notifyResult;
    @Column(name = "triggered_at", nullable = false) private LocalDateTime triggeredAt;
    @PrePersist public void prePersist() { if (triggeredAt == null) triggeredAt = LocalDateTime.now(); }
}
