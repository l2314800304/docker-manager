package com.dockermanager.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Compose 项目 DTO，聚合项目下所有服务的状态信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComposeProjectDTO {
    private String projectName;
    private String workingDir;
    private int totalServices;
    private int runningServices;
    private int stoppedServices;
    private List<ContainerInfoDTO> services;
}
