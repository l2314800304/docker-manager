package com.dockermanager.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 告警规则实体。定义监控指标的告警阈值和通知方式。
 * 支持指标: HOST_CPU, HOST_MEMORY, HOST_DISK, CONTAINER_CPU, CONTAINER_MEMORY, CONTAINER_STOPPED
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "alert_rule")
public class AlertRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "host_id") private Long hostId;
    @Column(name = "metric_type", nullable = false, length = 50) private String metricType;
    @Column(nullable = false) private double threshold;
    @Column(name = "compare_operator", nullable = false, length = 10) private String compareOperator;
    @Builder.Default @Column(name = "duration_seconds") private int durationSeconds = 0;
    @Column(name = "notify_type", nullable = false, length = 30) private String notifyType;
    @Column(name = "notify_target", nullable = false, length = 500) private String notifyTarget;
    @Column(name = "dingtalk_secret", length = 200) private String dingtalkSecret;
    @Builder.Default private boolean enabled = true;
    @Column(name = "last_triggered_at") private LocalDateTime lastTriggeredAt;
    @Builder.Default @Column(name = "cooldown_seconds") private int cooldownSeconds = 300;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
