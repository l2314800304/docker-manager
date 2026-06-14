package com.dockermanager.service;

import com.dockermanager.docker.ComposeProjectDiscovery;
import com.dockermanager.docker.DockerClientService;
import com.dockermanager.model.dto.ContainerInfoDTO;
import com.dockermanager.model.enums.AuditAction;
import com.github.dockerjava.api.model.Container;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 容器管理业务服务。
 *
 * <p>封装容器的查询和生命周期操作逻辑，并在每次操作时记录：</p>
 * <ul>
 *   <li><b>审计日志</b>（{@link AuditService}）— 谁在什么时候做了什么操作</li>
 *   <li><b>状态变更</b>（{@link ContainerStateTracker}）— 容器从什么状态变成了什么状态</li>
 * </ul>
 *
 * <p>所有操作方法都先获取当前容器信息（用于获取项目名、服务名、镜像等上下文），
 * 再执行 Docker 操作，最后记录审计和状态变更。即使获取容器信息失败（返回 null），
 * 仍会继续执行操作，仅审计记录中的上下文字段为 null。</p>
 *
 * @see ContainerController REST API 入口
 * @see AuditService 审计日志服务
 * @see ContainerStateTracker 容器状态追踪器
 */
@Service
public class ContainerService {

    private final DockerClientService dockerClientService;
    private final ComposeProjectDiscovery discovery;
    private final AuditService auditService;
    private final ContainerStateTracker stateTracker;

    public ContainerService(DockerClientService dockerClientService,
                           ComposeProjectDiscovery discovery,
                           AuditService auditService,
                           ContainerStateTracker stateTracker) {
        this.dockerClientService = dockerClientService;
        this.discovery = discovery;
        this.auditService = auditService;
        this.stateTracker = stateTracker;
    }

    /**
     * 获取宿主机上所有容器信息列表。
     *
     * <p>包括 Compose 管理的容器和非 Compose 容器。
     * 每个容器都经过 {@link ComposeProjectDiscovery#toContainerInfoDTO} 转换，
     * 包含服务名、项目名、端口、镜像 digest 等丰富信息。</p>
     *
     * @return 容器信息 DTO 列表
     */
    public List<ContainerInfoDTO> getAllContainers() {
        List<Container> containers = dockerClientService.listAllContainers();
        return containers.stream()
                .map(discovery::toContainerInfoDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据容器 ID 获取单个容器信息。
     *
     * <p>支持完整 ID 和前缀匹配（类似 docker CLI 的行为），
     * 遍历所有容器查找第一个匹配的。</p>
     *
     * @param containerId 容器 ID（完整或前缀）
     * @return 容器信息（如果找到），否则 empty
     */
    public Optional<ContainerInfoDTO> getContainer(String containerId) {
        try {
            var containers = dockerClientService.listAllContainers();
            return containers.stream()
                    .filter(c -> c.getId().equals(containerId) || c.getId().startsWith(containerId))
                    .findFirst()
                    .map(discovery::toContainerInfoDTO);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 获取容器的 Docker inspect 原始数据。
     *
     * <p>返回容器配置、状态、网络、挂载等完整信息的 Map 格式，
     * 用于高级调试和详细信息展示。</p>
     *
     * @param containerId 容器 ID
     * @return 包含 id、name、state、config、networkSettings、mounts 的 Map
     */
    public Map<String, Object> inspectContainer(String containerId) {
        var inspect = dockerClientService.inspectContainer(containerId);
        return Map.of(
                "id", inspect.getId(),
                "name", inspect.getName(),
                "state", inspect.getState(),
                "config", inspect.getConfig(),
                "networkSettings", inspect.getNetworkSettings(),
                "mounts", inspect.getMounts() != null ? inspect.getMounts() : List.of()
        );
    }

    /**
     * 重启容器（含审计日志和状态追踪）。
     *
     * <p>执行流程：获取当前状态 → 发送重启指令 → 记录审计日志 → 记录状态变更</p>
     *
     * @param containerId 容器 ID
     */
    public void restartContainer(String containerId) {
        // 获取当前容器信息（用于审计上下文和旧状态记录）
        ContainerInfoDTO currentInfo = getContainer(containerId).orElse(null);
        String oldState = currentInfo != null ? currentInfo.getState().name() : "UNKNOWN";

        dockerClientService.restartContainer(containerId);

        // 记录审计日志
        auditService.log(AuditAction.RESTART, containerId,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                "SUCCESS", null);

        // 记录状态变更历史
        stateTracker.recordStateChange(containerId,
                currentInfo != null ? currentInfo.getContainerName() : null,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                oldState, "RESTARTING",
                currentInfo != null ? currentInfo.getImage() : null,
                "Container restarted by user");
    }

    /**
     * 停止容器（含审计日志和状态追踪）。
     *
     * @param containerId 容器 ID
     */
    public void stopContainer(String containerId) {
        ContainerInfoDTO currentInfo = getContainer(containerId).orElse(null);
        String oldState = currentInfo != null ? currentInfo.getState().name() : "UNKNOWN";

        dockerClientService.stopContainer(containerId);

        auditService.log(AuditAction.STOP, containerId,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                "SUCCESS", null);

        stateTracker.recordStateChange(containerId,
                currentInfo != null ? currentInfo.getContainerName() : null,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                oldState, "STOPPED",
                currentInfo != null ? currentInfo.getImage() : null,
                "Container stopped by user");
    }

    /**
     * 启动容器（含审计日志和状态追踪）。
     *
     * @param containerId 容器 ID
     */
    public void startContainer(String containerId) {
        ContainerInfoDTO currentInfo = getContainer(containerId).orElse(null);
        String oldState = currentInfo != null ? currentInfo.getState().name() : "UNKNOWN";

        dockerClientService.startContainer(containerId);

        auditService.log(AuditAction.START, containerId,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                "SUCCESS", null);

        stateTracker.recordStateChange(containerId,
                currentInfo != null ? currentInfo.getContainerName() : null,
                currentInfo != null ? currentInfo.getProjectName() : null,
                currentInfo != null ? currentInfo.getServiceName() : null,
                oldState, "RUNNING",
                currentInfo != null ? currentInfo.getImage() : null,
                "Container started by user");
    }
}
