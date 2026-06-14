package com.dockermanager.domain.dto;

import com.dockermanager.domain.enums.ContainerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 容器信息 DTO，包含容器的完整元数据 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInfoDTO {
    private String containerId;
    private String containerName;
    private String serviceName;
    private String projectName;
    private String image;
    private String imageId;
    private ContainerState state;
    private String status;
    private List<String> ports;
    private List<String> digests;
    private Map<String, String> labels;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private String composeWorkingDir;
}
