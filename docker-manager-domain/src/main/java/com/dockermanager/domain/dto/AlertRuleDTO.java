package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警规则 DTO */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertRuleDTO {
    private Long id;
    private String name;
    private Long hostId;
    private String hostName;
    private String metricType;
    private double threshold;
    private String compareOperator;
    private int durationSeconds;
    private String notifyType;
    private String notifyTarget;
    private boolean enabled;
    private int cooldownSeconds;
    private String lastTriggeredAt;
}
