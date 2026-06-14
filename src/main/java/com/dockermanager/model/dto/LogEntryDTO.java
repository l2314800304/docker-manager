package com.dockermanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {
    private String streamType; // stdout or stderr
    private String line;
    private long timestamp;
}
