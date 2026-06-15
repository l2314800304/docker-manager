package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 告警记录 DTO */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertRecordDTO {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private Long hostId;
    private String hostName;
    private String metricType;
    private double metricValue;
    private double threshold;
    private String message;
    private String notifyStatus;
    private String notifyResult;
    private String triggeredAt;
}
