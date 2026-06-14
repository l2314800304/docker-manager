package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.dto.ContainerInfoDTO;
import com.dockermanager.domain.dto.ContainerStatsDTO;
import com.dockermanager.domain.dto.ComposeProjectDTO;
import com.dockermanager.domain.dto.FileEntryDTO;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Docker 基础设施出站端口（Outbound Port）。
 *
 * <p>定义应用层调用 Docker 基础设施的契约。
 * 由 Infrastructure 层的 Docker 适配器实现。</p>
 *
 * <p>六边形架构中的"右侧端口"——系统通过此接口驱动外部资源。</p>
 */
public interface DockerAdapterPort {

    // ==================== Docker 基础操作 ====================

    /** 获取 Docker 版本信息 */
    Map<String, Object> getVersion();

    /** 获取 Docker 系统信息 */
    Map<String, Object> getDockerInfo();

    // ==================== 容器操作 ====================

    /** 列出所有容器并转换为 DTO */
    List<ContainerInfoDTO> listAllContainerDTOs();

    /** 获取容器详细信息（inspect） */
    Map<String, Object> inspectContainer(String containerId);

    /** 重启容器 */
    void restartContainer(String containerId);

    /** 停止容器 */
    void stopContainer(String containerId);

    /** 启动容器 */
    void startContainer(String containerId);

    // ==================== Compose 项目发现 ====================

    /** 发现所有 Compose 项目 */
    List<ComposeProjectDTO> discoverProjects();

    /** 获取指定项目 */
    Optional<ComposeProjectDTO> getProject(String projectName);

    // ==================== 资源统计 ====================

    /** 获取 Stats 快照 */
    ContainerStatsDTO getStatsSnapshot(String containerId);

    /** 启动持续 Stats 采集（WebSocket 推送用） */
    String startStatsCollecting(String containerId, Consumer<ContainerStatsDTO> onStats);

    /** 停止 Stats 采集器 */
    void stopStatsCollector(String collectorId);

    /** 停止所有采集器 */
    void stopAllCollectors();

    // ==================== 日志 ====================

    /** 获取历史日志 */
    String getHistoryLogs(String containerId, int tail, Integer since);

    /** 启动日志流（WebSocket 推送用） */
    String streamLogs(String containerId, Consumer<String> onLog, Consumer<Throwable> onError, Runnable onComplete);

    /** 停止日志流 */
    void stopLogStream(String streamId);

    /** 停止所有日志流 */
    void stopAllLogStreams();

    // ==================== 文件系统 ====================

    /** 列出目录 */
    List<FileEntryDTO> listDirectory(String containerId, String path);

    /** 读取文件 */
    String readFile(String containerId, String filePath);

    /** 下载文件 */
    InputStream downloadFile(String containerId, String filePath);

    // ==================== 镜像管理 ====================

    /** 异步更新服务镜像（返回任务 ID） */
    String updateServiceImage(String projectName, String serviceName,
                              String image, String newTag,
                              boolean autoRestart, boolean rollbackOnFailure,
                              Consumer<String> onProgress);

    /** 查询任务状态 */
    Map<String, String> getTaskStatus(String taskId);

    /** 查询镜像可用 Tag */
    List<String> getImageTags(String imageName);
}
