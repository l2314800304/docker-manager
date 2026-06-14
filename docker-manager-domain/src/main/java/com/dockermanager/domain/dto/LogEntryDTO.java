package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 日志条目 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {
    private String streamType; // stdout or stderr
    private String line;
    private long timestamp;
}
