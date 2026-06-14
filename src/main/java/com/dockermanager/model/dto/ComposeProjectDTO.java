package com.dockermanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
