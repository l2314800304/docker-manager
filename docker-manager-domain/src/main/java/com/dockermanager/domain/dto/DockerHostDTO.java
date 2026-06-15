package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 宿主机信息 DTO（用于 API 响应，不含敏感信息） */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DockerHostDTO {
    private Long id;
    private String name;
    private String connectionType;
    private String connectionUrl;
    private boolean tlsEnabled;
    private boolean enabled;
    private String status;
    private String dockerVersion;
    private LocalDateTime lastConnectedAt;
    private LocalDateTime createdAt;
}
