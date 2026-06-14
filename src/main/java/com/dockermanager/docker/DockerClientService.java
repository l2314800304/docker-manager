package com.dockermanager.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StatsCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Docker API 客户端服务封装。
 *
 * <p>对 docker-java 库的 {@link DockerClient} 进行二次封装，提供统一的 API 调用入口。
 * 所有其他 Docker 相关组件（{@link ComposeProjectDiscovery}、{@link ContainerLogStreamer} 等）
 * 都通过此服务访问 Docker daemon。</p>
 *
 * <h3>功能分组：</h3>
 * <ul>
 *   <li><b>容器操作</b>：列表、详情（inspect）、按标签过滤</li>
 *   <li><b>生命周期</b>：启动、停止、重启、删除</li>
 *   <li><b>镜像操作</b>：列表、拉取</li>
 *   <li><b>日志</b>：获取日志流命令（供 {@link ContainerLogStreamer} 使用）</li>
 *   <li><b>Stats</b>：获取资源统计命令（供 {@link ContainerStatsCollector} 使用）</li>
 *   <li><b>文件系统</b>：从容器复制归档文件（tar 格式）</li>
 *   <li><b>Exec</b>：在容器内执行命令（用于文件系统浏览的 ls/cat 操作）</li>
 * </ul>
 *
 * @see DockerClientConfig Docker 客户端连接配置
 */
@Service
public class DockerClientService {

    private static final Logger log = LoggerFactory.getLogger(DockerClientService.class);
    private final DockerClient dockerClient;

    public DockerClientService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    /** 获取原始 DockerClient 实例（供需要直接操作的高级场景使用） */
    public DockerClient getDockerClient() {
        return dockerClient;
    }

    /** 获取 Docker daemon 版本信息 */
    public Version getVersion() {
        return dockerClient.versionCmd().exec();
    }

    /** 获取 Docker daemon 系统信息（容器数、镜像数、存储驱动等） */
    public Info getDockerInfo() {
        return dockerClient.infoCmd().exec();
    }

    // ==================== 容器操作 ====================

    /**
     * 列出所有容器（包括运行中和已停止的）。
     *
     * @return 容器列表（不含详细 inspect 信息）
     */
    public List<Container> listAllContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }

    /**
     * 按 Docker 标签过滤容器列表。
     *
     * <p>常用于查找属于特定 Compose 项目的容器，如
     * {@code label="com.docker.compose.project"}。</p>
     *
     * @param label 标签名（值匹配空字符串表示只要存在该标签即可）
     * @return 匹配的容器列表
     */
    public List<Container> listContainersByLabel(String label) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(label, ""))
                .exec();
    }

    /**
     * 获取容器的详细信息（等同 {@code docker inspect}）。
     *
     * <p>返回包含配置、网络、挂载、状态等全量信息。</p>
     *
     * @param containerId 容器 ID（完整或前缀）
     * @return InspectContainerResponse 详细信息
     */
    public InspectContainerResponse inspectContainer(String containerId) {
        return dockerClient.inspectContainerCmd(containerId).exec();
    }

    // ==================== 镜像操作 ====================

    /** 列出本地所有镜像 */
    public List<Image> listImages() {
        return dockerClient.listImagesCmd().exec();
    }

    /**
     * 拉取 Docker 镜像。
     *
     * @param imageName 镜像名（如 "nginx"、"myregistry/app"）
     * @param tag       标签（null 时默认 "latest"）
     * @param callback  拉取进度回调（接收每一层的下载状态）
     */
    public void pullImage(String imageName, String tag, PullImageResultCallback callback) {
        dockerClient.pullImageCmd(imageName)
                .withTag(tag != null ? tag : "latest")
                .exec(callback);
    }

    // ==================== 生命周期操作 ====================

    /** 停止容器（发送 SIGTERM，等待超时后 SIGKILL） */
    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    /** 启动已停止的容器 */
    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    /** 重启容器（先停止再启动） */
    public void restartContainer(String containerId) {
        dockerClient.restartContainerCmd(containerId).exec();
    }

    /**
     * 删除容器。
     *
     * @param containerId 容器 ID
     * @param force       true=强制删除（即使容器仍在运行）
     */
    public void removeContainer(String containerId, boolean force) {
        dockerClient.removeContainerCmd(containerId).withForce(force).exec();
    }

    // ==================== 日志操作 ====================

    /**
     * 创建日志容器命令（供 {@link ContainerLogStreamer} 配置和执行）。
     *
     * <p>返回的 {@link LogContainerCmd} 需要进一步配置
     * （stdout/stderr/follow/tail/timestamps）后调用 exec()。</p>
     *
     * @param containerId 容器 ID
     * @return 可配置的日志命令对象
     */
    public LogContainerCmd logContainerCmd(String containerId) {
        return dockerClient.logContainerCmd(containerId);
    }

    // ==================== Stats 操作 ====================

    /**
     * 创建资源统计命令（供 {@link ContainerStatsCollector} 配置和执行）。
     *
     * <p>返回的 {@link StatsCmd} 支持流式获取（exec + callback）
     * 或单次快照获取（exec + awaitCompletion）。</p>
     *
     * @param containerId 容器 ID
     * @return Stats 命令对象
     */
    public StatsCmd statsCmd(String containerId) {
        return dockerClient.statsCmd(containerId);
    }

    // ==================== 文件系统操作 ====================

    /**
     * 从容器中复制文件/目录（tar 归档格式）。
     *
     * <p>返回 tar 格式的 InputStream，调用方需自行解压。
     * 这是 {@link ContainerFileSystemAccessor} 的 tar 降级方案的基础。</p>
     *
     * @param containerId  容器 ID
     * @param resourcePath 容器内的文件或目录路径
     * @return tar 归档的输入流
     */
    public InputStream copyArchiveFromContainer(String containerId, String resourcePath) {
        return dockerClient.copyArchiveFromContainerCmd(containerId, resourcePath).exec();
    }

    // ==================== Exec 操作 ====================

    /**
     * 在容器内执行命令并返回标准输出。
     *
     * <p>使用 Docker exec API 在运行中的容器内执行指定命令。
     * 主要用于：</p>
     * <ul>
     *   <li>{@code ls -la} — 文件系统目录列表</li>
     *   <li>{@code cat <file>} — 读取文件内容</li>
     * </ul>
     *
     * <p>实现步骤：</p>
     * <ol>
     *   <li>创建 exec 实例（附加 stdout + stderr）</li>
     *   <li>启动 exec 并等待完成</li>
     *   <li>将输出流转为 UTF-8 字符串返回</li>
     * </ol>
     *
     * @param containerId 容器 ID
     * @param command     命令及参数（如 "ls", "-la", "/etc"）
     * @return 命令的标准输出 + 标准错误合并结果
     * @throws RuntimeException exec 被中断时抛出
     */
    public String execInContainer(String containerId, String... command) {
        // 步骤 1: 创建 exec 实例
        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec();

        // 步骤 2: 执行并捕获输出
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execCreate.getId())
                    .exec(new com.github.dockerjava.core.command.ExecStartResultCallback(outputStream, outputStream))
                    .awaitCompletion();  // 阻塞等待命令完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 恢复中断状态
            throw new RuntimeException("Exec interrupted", e);
        }

        // 步骤 3: 转为字符串返回
        return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
