package com.dockermanager.model.dto;

import com.dockermanager.model.enums.ContainerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
