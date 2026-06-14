package com.dockermanager.application.service;

import com.dockermanager.application.port.outbound.DockerAdapterPort;
import com.dockermanager.application.port.outbound.AuditRepositoryPort;
import com.dockermanager.application.port.outbound.StatusRecordRepositoryPort;
import com.dockermanager.domain.dto.*;
import com.dockermanager.domain.entity.AuditLog;
import com.dockermanager.domain.entity.StatusRecord;
import com.dockermanager.domain.enums.AuditAction;
import com.dockermanager.domain.port.inbound.DockerOperationPort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 容器操作应用服务。编排 Docker 操作、审计日志和状态追踪。
 */
@Service
public class ContainerAppService implements DockerOperationPort {

    private final DockerAdapterPort dockerAdapter;
    private final AuditRepositoryPort auditRepository;
    private final StatusRecordRepositoryPort statusRecordRepository;

    public ContainerAppService(DockerAdapterPort dockerAdapter,
                               AuditRepositoryPort auditRepository,
                               StatusRecordRepositoryPort statusRecordRepository) {
        this.dockerAdapter = dockerAdapter;
        this.auditRepository = auditRepository;
        this.statusRecordRepository = statusRecordRepository;
    }

    @Override
    public List<ContainerInfoDTO> getAllContainers() {
        return dockerAdapter.listAllContainerDTOs();
    }

    @Override
    public Optional<ContainerInfoDTO> getContainer(String containerId) {
        return dockerAdapter.listAllContainerDTOs().stream()
                .filter(c -> c.getContainerId().equals(containerId) || c.getContainerId().startsWith(containerId))
                .findFirst();
    }

    @Override
    public Map<String, Object> inspectContainer(String containerId) {
        return dockerAdapter.inspectContainer(containerId);
    }

    @Override
    public void restartContainer(String containerId) {
        ContainerInfoDTO info = getContainer(containerId).orElse(null);
        String oldState = info != null ? info.getState().name() : "UNKNOWN";

        dockerAdapter.restartContainer(containerId);

        logAudit(AuditAction.RESTART, containerId, info, "SUCCESS");
        recordStateChange(containerId, info, oldState, "RESTARTING", "Container restarted by user");
    }

    @Override
    public void stopContainer(String containerId) {
        ContainerInfoDTO info = getContainer(containerId).orElse(null);
        String oldState = info != null ? info.getState().name() : "UNKNOWN";

        dockerAdapter.stopContainer(containerId);

        logAudit(AuditAction.STOP, containerId, info, "SUCCESS");
        recordStateChange(containerId, info, oldState, "STOPPED", "Container stopped by user");
    }

    @Override
    public void startContainer(String containerId) {
        ContainerInfoDTO info = getContainer(containerId).orElse(null);
        String oldState = info != null ? info.getState().name() : "UNKNOWN";

        dockerAdapter.startContainer(containerId);

        logAudit(AuditAction.START, containerId, info, "SUCCESS");
        recordStateChange(containerId, info, oldState, "RUNNING", "Container started by user");
    }

    @Override
    public List<ComposeProjectDTO> discoverProjects() {
        return dockerAdapter.discoverProjects();
    }

    @Override
    public Optional<ComposeProjectDTO> getProject(String projectName) {
        return dockerAdapter.getProject(projectName);
    }

    @Override
    public ContainerStatsDTO getStatsSnapshot(String containerId) {
        return dockerAdapter.getStatsSnapshot(containerId);
    }

    @Override
    public List<ContainerStatsDTO> getAllStats() {
        var containers = getAllContainers();
        var running = containers.stream()
                .filter(c -> c.getState() == com.dockermanager.domain.enums.ContainerState.RUNNING)
                .collect(Collectors.toList());

        return running.parallelStream()
                .map(c -> {
                    try { return dockerAdapter.getStatsSnapshot(c.getContainerId()); }
                    catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<FileEntryDTO> listDirectory(String containerId, String path) {
        return dockerAdapter.listDirectory(containerId, path);
    }

    @Override
    public String readFile(String containerId, String filePath) {
        return dockerAdapter.readFile(containerId, filePath);
    }

    @Override
    public String updateServiceImage(String projectName, String serviceName, ImageUpdateRequest request) {
        String image = request.getImage() != null ? request.getImage() : serviceName;
        String newTag = request.getNewTag() != null ? request.getNewTag() : "latest";

        String taskId = dockerAdapter.updateServiceImage(projectName, serviceName,
                image, newTag, request.isAutoRestart(), request.isRollbackOnFailure(),
                progress -> {});

        AuditLog auditLog = AuditLog.builder()
                .action(AuditAction.UPDATE_TAG)
                .projectName(projectName)
                .serviceName(serviceName)
                .result("STARTED")
                .detail("Updated " + request.getImage() + ":" + request.getCurrentTag() + " -> " + newTag)
                .build();
        auditRepository.save(auditLog);

        return taskId;
    }

    @Override
    public Map<String, String> getTaskStatus(String taskId) {
        return dockerAdapter.getTaskStatus(taskId);
    }

    @Override
    public List<String> getImageTags(String imageName) {
        return dockerAdapter.getImageTags(imageName);
    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", java.time.LocalDateTime.now().toString());

        try {
            result.put("docker", dockerAdapter.getVersion());
        } catch (Exception e) {
            result.put("docker", Map.of("status", "DOWN", "error", e.getMessage()));
            result.put("status", "DEGRADED");
        }

        try {
            result.put("dockerInfo", dockerAdapter.getDockerInfo());
        } catch (Exception ignored) {}

        return result;
    }

    // ==================== 内部辅助方法 ====================

    private void logAudit(AuditAction action, String containerId, ContainerInfoDTO info, String result) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .containerId(containerId)
                .projectName(info != null ? info.getProjectName() : null)
                .serviceName(info != null ? info.getServiceName() : null)
                .result(result)
                .build();
        auditRepository.save(log);
    }

    private void recordStateChange(String containerId, ContainerInfoDTO info,
                                   String oldState, String newState, String detail) {
        StatusRecord record = StatusRecord.builder()
                .containerId(containerId)
                .containerName(info != null ? info.getContainerName() : null)
                .projectName(info != null ? info.getProjectName() : null)
                .serviceName(info != null ? info.getServiceName() : null)
                .oldState(oldState)
                .newState(newState)
                .image(info != null ? info.getImage() : null)
                .detail(detail)
                .build();
        statusRecordRepository.save(record);
    }
}
