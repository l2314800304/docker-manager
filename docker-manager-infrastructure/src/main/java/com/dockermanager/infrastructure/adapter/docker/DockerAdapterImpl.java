package com.dockermanager.infrastructure.adapter.docker;

import com.dockermanager.application.port.outbound.DockerAdapterPort;
import com.dockermanager.domain.dto.*;
import com.dockermanager.infrastructure.adapter.docker.internal.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * Docker 适配器实现。实现应用层的 DockerAdapterPort 出站端口。
 * 内部委托给各 Docker 组件（Client/Discovery/LogStreamer/StatsCollector/FS/Lifecycle）。
 */
@Component
public class DockerAdapterImpl implements DockerAdapterPort {

    private final DockerClientBridge dockerClient;
    private final ComposeProjectBridge projectDiscovery;
    private final LogStreamBridge logStreamer;
    private final StatsBridge statsCollector;
    private final FileSystemBridge fileSystemAccessor;
    private final LifecycleBridge lifecycleManager;

    public DockerAdapterImpl(DockerClientBridge dockerClient,
                             ComposeProjectBridge projectDiscovery,
                             LogStreamBridge logStreamer,
                             StatsBridge statsCollector,
                             FileSystemBridge fileSystemAccessor,
                             LifecycleBridge lifecycleManager) {
        this.dockerClient = dockerClient;
        this.projectDiscovery = projectDiscovery;
        this.logStreamer = logStreamer;
        this.statsCollector = statsCollector;
        this.fileSystemAccessor = fileSystemAccessor;
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public Map<String, Object> getVersion() {
        var v = dockerClient.getVersion();
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("status", "UP");
        docker.put("version", v.getVersion());
        docker.put("apiVersion", v.getApiVersion());
        docker.put("os", v.getOs());
        docker.put("arch", v.getArch());
        return docker;
    }

    @Override
    public Map<String, Object> getDockerInfo() {
        var info = dockerClient.getDockerInfo();
        Map<String, Object> infoMap = new LinkedHashMap<>();
        infoMap.put("containers", info.getContainers());
        infoMap.put("containersRunning", info.getContainersRunning());
        infoMap.put("containersStopped", info.getContainersStopped());
        infoMap.put("images", info.getImages());
        infoMap.put("serverName", info.getName());
        return infoMap;
    }

    @Override
    public List<ContainerInfoDTO> listAllContainerDTOs() {
        return dockerClient.listAllContainers().stream()
                .map(projectDiscovery::toContainerInfoDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Map<String, Object> inspectContainer(String containerId) {
        var inspect = dockerClient.inspectContainer(containerId);
        return Map.of(
                "id", inspect.getId(), "name", inspect.getName(),
                "state", inspect.getState(), "config", inspect.getConfig(),
                "networkSettings", inspect.getNetworkSettings(),
                "mounts", inspect.getMounts() != null ? inspect.getMounts() : List.of()
        );
    }

    @Override
    public void restartContainer(String containerId) { dockerClient.restartContainer(containerId); }
    @Override
    public void stopContainer(String containerId) { dockerClient.stopContainer(containerId); }
    @Override
    public void startContainer(String containerId) { dockerClient.startContainer(containerId); }

    @Override
    public List<ComposeProjectDTO> discoverProjects() { return projectDiscovery.discoverProjects(); }
    @Override
    public Optional<ComposeProjectDTO> getProject(String name) { return projectDiscovery.getProject(name); }

    @Override
    public ContainerStatsDTO getStatsSnapshot(String containerId) { return statsCollector.getStatsSnapshot(containerId); }
    @Override
    public String startStatsCollecting(String containerId, Consumer<ContainerStatsDTO> onStats) { return statsCollector.startCollecting(containerId, onStats); }
    @Override
    public void stopStatsCollector(String id) { statsCollector.stopCollector(id); }
    @Override
    public void stopAllCollectors() { statsCollector.stopAllCollectors(); }

    @Override
    public String getHistoryLogs(String containerId, int tail, Integer since) { return logStreamer.getHistoryLogs(containerId, tail, since); }
    @Override
    public String streamLogs(String containerId, Consumer<String> onLog, Consumer<Throwable> onError, Runnable onComplete) { return logStreamer.streamLogs(containerId, onLog, onError, onComplete); }
    @Override
    public void stopLogStream(String streamId) { logStreamer.stopStream(streamId); }
    @Override
    public void stopAllLogStreams() { logStreamer.stopAllStreams(); }

    @Override
    public List<FileEntryDTO> listDirectory(String containerId, String path) { return fileSystemAccessor.listDirectory(containerId, path); }
    @Override
    public String readFile(String containerId, String filePath) { return fileSystemAccessor.readFile(containerId, filePath); }
    @Override
    public InputStream downloadFile(String containerId, String filePath) { return fileSystemAccessor.downloadFile(containerId, filePath); }

    @Override
    public String updateServiceImage(String projectName, String serviceName, String image, String newTag, boolean autoRestart, boolean rollbackOnFailure, Consumer<String> onProgress) {
        ImageUpdateRequest request = ImageUpdateRequest.builder()
                .image(image).newTag(newTag).autoRestart(autoRestart).rollbackOnFailure(rollbackOnFailure).build();
        return lifecycleManager.updateServiceImage(projectName, serviceName, request, onProgress);
    }
    @Override
    public Map<String, String> getTaskStatus(String taskId) { return lifecycleManager.getTaskStatus(taskId); }
    @Override
    public List<String> getImageTags(String imageName) { return lifecycleManager.getImageTags(imageName); }
}
