package com.dockermanager.domain.port.inbound;

import com.dockermanager.domain.dto.ComposeProjectDTO;
import com.dockermanager.domain.dto.ContainerInfoDTO;
import com.dockermanager.domain.dto.ContainerStatsDTO;
import com.dockermanager.domain.dto.FileEntryDTO;
import com.dockermanager.domain.dto.ImageUpdateRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Docker 操作入站端口（Inbound Port / Use Case Interface）。
 *
 * <p>定义应用层需要实现的核心业务操作契约。
 * 由基础设施层的 Controller/Adapter 调用，应用层的 Service 实现。</p>
 *
 * <p>六边形架构中的"左侧端口"——驱动端（Driver）通过此接口驱动系统。</p>
 */
public interface DockerOperationPort {

    // ==================== 容器查询 ====================

    /** 获取所有容器信息列表 */
    List<ContainerInfoDTO> getAllContainers();

    /** 按 ID 获取单个容器信息（支持完整 ID 或前缀匹配） */
    Optional<ContainerInfoDTO> getContainer(String containerId);

    /** 获取容器 Docker inspect 原始数据 */
    Map<String, Object> inspectContainer(String containerId);

    // ==================== 容器生命周期 ====================

    /** 重启容器（含审计日志和状态追踪） */
    void restartContainer(String containerId);

    /** 停止容器（含审计日志和状态追踪） */
    void stopContainer(String containerId);

    /** 启动容器（含审计日志和状态追踪） */
    void startContainer(String containerId);

    // ==================== Compose 项目 ====================

    /** 发现所有 Compose 项目 */
    List<ComposeProjectDTO> discoverProjects();

    /** 获取指定项目详情 */
    Optional<ComposeProjectDTO> getProject(String projectName);

    // ==================== 资源统计 ====================

    /** 获取单个容器的 Stats 快照 */
    ContainerStatsDTO getStatsSnapshot(String containerId);

    /** 获取所有运行中容器的 Stats 汇总 */
    List<ContainerStatsDTO> getAllStats();

    // ==================== 文件系统 ====================

    /** 列出容器内目录的文件条目 */
    List<FileEntryDTO> listDirectory(String containerId, String path);

    /** 读取容器内文件内容 */
    String readFile(String containerId, String filePath);

    // ==================== 镜像更新 ====================

    /** 异步更新服务镜像版本，返回任务 ID */
    String updateServiceImage(String projectName, String serviceName, ImageUpdateRequest request);

    /** 查询更新任务状态 */
    Map<String, String> getTaskStatus(String taskId);

    /** 查询镜像可用 Tag */
    List<String> getImageTags(String imageName);

    // ==================== 健康检查 ====================

    /** 获取系统和 Docker 连接状态 */
    Map<String, Object> getHealth();
}
