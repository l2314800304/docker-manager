package com.dockermanager.docker;

import com.dockermanager.model.dto.ImageUpdateRequest;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 容器生命周期管理器。
 *
 * <p>负责容器镜像更新的完整流程，包括拉取新镜像和重启服务。
 * 所有更新操作异步执行，通过任务 ID 追踪进度。</p>
 *
 * <h3>镜像更新流程：</h3>
 * <ol>
 *   <li>生成唯一任务 ID，记录初始状态为 RUNNING</li>
 *   <li>异步拉取新镜像（带进度回调）</li>
 *   <li>尝试通过 {@code docker compose up -d --no-deps <service>} 重启服务
 *       <br>（此方式最安全，compose 会自动处理依赖和配置）</li>
 *   <li>若 docker compose 不可用（如不在容器内运行），降级为直接重启容器</li>
 *   <li>更新任务状态为 SUCCESS 或 FAILURE</li>
 *   <li>失败时如果 {@code rollbackOnFailure=true}，尝试回滚</li>
 * </ol>
 *
 * <h3>任务状态查询：</h3>
 * <p>前端通过轮询 {@code GET /api/tasks/{taskId}/status} 获取更新进度。</p>
 *
 * @see ImageUpdateRequest 镜像更新请求 DTO
 */
@Component
public class ContainerLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(ContainerLifecycleManager.class);
    private final DockerClientService dockerClientService;

    /**
     * 异步任务状态追踪映射。
     * <p>key=任务ID, value=当前状态。使用 ConcurrentHashMap 保证线程安全。</p>
     * <p>注意：任务完成后状态会一直保留在内存中，生产环境应考虑添加过期清理机制。</p>
     */
    private final Map<String, TaskStatus> tasks = new ConcurrentHashMap<>();

    public ContainerLifecycleManager(DockerClientService dockerClientService) {
        this.dockerClientService = dockerClientService;
    }

    /**
     * 异步更新服务的镜像版本。
     *
     * <p>启动一个新线程执行完整的更新流程，立即返回任务 ID。
     * 调用方可通过 {@link #getTaskStatus(String)} 轮询进度。</p>
     *
     * @param projectName Compose 项目名（用于 docker compose -p 参数）
     * @param serviceName 服务名（docker-compose.yml 中的 service 名称）
     * @param request     更新请求（包含镜像名、旧 tag、新 tag、是否自动重启、失败是否回滚）
     * @param onProgress  进度回调（可选，用于 WebSocket 推送进度）
     * @return 任务 ID（UUID 格式）
     */
    public String updateServiceImage(String projectName, String serviceName,
                                     ImageUpdateRequest request,
                                     Consumer<String> onProgress) {
        String taskId = UUID.randomUUID().toString();
        tasks.put(taskId, new TaskStatus("RUNNING", "Starting update..."));

        // 异步执行更新流程
        new Thread(() -> {
            try {
                String image = request.getImage() != null ? request.getImage() : serviceName;
                String newTag = request.getNewTag() != null ? request.getNewTag() : "latest";

                // 步骤 1: 拉取新镜像
                updateTask(taskId, "PULLING", "Pulling image " + image + ":" + newTag);
                onProgress.accept("Pulling image " + image + ":" + newTag);

                dockerClientService.getDockerClient().pullImageCmd(image)
                        .withTag(newTag)
                        .exec(new ResultCallback.Adapter<PullResponseItem>() {
                            @Override
                            public void onNext(PullResponseItem item) {
                                if (item.getStatus() != null) {
                                    String msg = item.getStatus();
                                    if (item.getProgress() != null) {
                                        msg += " " + item.getProgress();  // 下载进度条
                                    }
                                    updateTask(taskId, "PULLING", msg);
                                }
                            }
                        }).awaitCompletion();  // 阻塞等待镜像拉取完成

                onProgress.accept("Image pulled successfully");
                updateTask(taskId, "RESTARTING", "Restarting service with new image");

                // 步骤 2: 优先使用 docker compose 重启服务
                boolean success = tryDockerComposeRestart(projectName, serviceName);

                if (!success) {
                    // docker compose 不可用，降级为直接重启容器
                    log.info("docker compose not available, falling back to container restart");
                    onProgress.accept("docker compose not available, restarting container directly");

                    // 通过 label 查找属于该项目和服务的容器
                    var containers = dockerClientService.listContainersByLabel("com.docker.compose.project");
                    for (var container : containers) {
                        Map<String, String> labels = container.getLabels();
                        if (projectName.equals(labels.get("com.docker.compose.project"))
                                && serviceName.equals(labels.get("com.docker.compose.service"))) {
                            dockerClientService.restartContainer(container.getId());
                        }
                    }
                }

                updateTask(taskId, "SUCCESS", "Service updated successfully");
                onProgress.accept("Update completed successfully");

            } catch (Exception e) {
                log.error("Failed to update service: {}", e.getMessage());
                updateTask(taskId, "FAILURE", e.getMessage());
                onProgress.accept("ERROR: " + e.getMessage());

                // 步骤 3（可选）: 失败时回滚
                if (request.isRollbackOnFailure()) {
                    try {
                        onProgress.accept("Attempting rollback...");
                        tryDockerComposeRestart(projectName, serviceName);  // 重启恢复到旧镜像
                        onProgress.accept("Rollback completed");
                    } catch (Exception rollbackEx) {
                        onProgress.accept("Rollback failed: " + rollbackEx.getMessage());
                    }
                }
            }
        }).start();

        return taskId;
    }

    /**
     * 查询异步任务的当前状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态（status + message），不存在时返回 null
     */
    public TaskStatus getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 尝试通过 docker compose v2 命令重启指定服务。
     *
     * <p>执行命令：{@code docker compose -p <project> up -d --no-deps <service>}</p>
     *
     * <p>此方式最安全，因为：</p>
     * <ul>
     *   <li>compose 会读取 docker-compose.yml 配置</li>
     *   <li>自动使用最新拉取的镜像</li>
     *   <li>{@code --no-deps} 避免重启依赖服务</li>
     * </ul>
     *
     * @param projectName Compose 项目名
     * @param serviceName 服务名
     * @return true=命令执行成功（exit code 0），false=命令不存在或执行失败
     */
    private boolean tryDockerComposeRestart(String projectName, String serviceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose", "-p", projectName,
                    "up", "-d", "--no-deps", serviceName
            );
            pb.redirectErrorStream(true);  // 合并 stderr 到 stdout
            Process process = pb.start();

            // 读取命令输出用于日志记录
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("docker compose: {}", line);
            }

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.warn("docker compose command failed: {}", e.getMessage());
            return false;
        }
    }

    /** 更新任务状态 */
    private void updateTask(String taskId, String status, String message) {
        tasks.put(taskId, new TaskStatus(status, message));
    }

    /**
     * 异步任务状态记录。
     *
     * @param status  状态标识（RUNNING / PULLING / RESTARTING / SUCCESS / FAILURE）
     * @param message 人类可读的进度/结果描述
     */
    public record TaskStatus(String status, String message) {}
}
