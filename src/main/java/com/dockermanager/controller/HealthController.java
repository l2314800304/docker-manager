package com.dockermanager.controller;

import com.dockermanager.docker.DockerClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查控制器。
 *
 * <p>提供系统和 Docker daemon 的连接状态检查端点。
 * 此端点为公开访问（无需认证），用于：</p>
 * <ul>
 *   <li>Docker HEALTHCHECK 指令的探针</li>
 *   <li>前端 Header 的 Docker 连接状态指示器</li>
 *   <li>外部监控系统（如 Prometheus BlackBox Exporter）集成</li>
 * </ul>
 *
 * <h3>响应示例：</h3>
 * <pre>
 * {
 *   "status": "UP",                    // 或 "DEGRADED"
 *   "timestamp": "2024-01-15T10:30:00",
 *   "docker": {
 *     "status": "UP",                  // 或 "DOWN"
 *     "version": "24.0.7",
 *     "apiVersion": "1.43",
 *     "os": "linux",
 *     "arch": "amd64"
 *   },
 *   "dockerInfo": {
 *     "containers": 15,
 *     "containersRunning": 12,
 *     "containersStopped": 3,
 *     "images": 8,
 *     "serverName": "docker-host"
 *   }
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DockerClientService dockerClientService;

    public HealthController(DockerClientService dockerClientService) {
        this.dockerClientService = dockerClientService;
    }

    /**
     * 系统健康检查端点。
     *
     * <p>依次检查：</p>
     * <ol>
     *   <li>Docker daemon 连接状态（version API）</li>
     *   <li>Docker 系统信息（容器数、镜像数等）</li>
     * </ol>
     *
     * <p>任一检查失败不影响其他检查的执行，最终返回汇总结果。
     * Docker 连接失败时 status 为 "DEGRADED" 而非 "DOWN"，
     * 因为应用本身仍在运行，只是 Docker 功能不可用。</p>
     *
     * @return 200 + 健康状态 JSON
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", java.time.LocalDateTime.now().toString());

        // 检查 1: Docker daemon 连接状态
        try {
            var version = dockerClientService.getVersion();
            Map<String, Object> docker = new LinkedHashMap<>();
            docker.put("status", "UP");
            docker.put("version", version.getVersion());
            docker.put("apiVersion", version.getApiVersion());
            docker.put("os", version.getOs());
            docker.put("arch", version.getArch());
            result.put("docker", docker);
        } catch (Exception e) {
            // Docker 连接失败，标记为降级状态
            Map<String, Object> docker = new LinkedHashMap<>();
            docker.put("status", "DOWN");
            docker.put("error", e.getMessage());
            result.put("docker", docker);
            result.put("status", "DEGRADED");
        }

        // 检查 2: Docker 系统信息摘要
        try {
            var info = dockerClientService.getDockerInfo();
            Map<String, Object> infoMap = new LinkedHashMap<>();
            infoMap.put("containers", info.getContainers());
            infoMap.put("containersRunning", info.getContainersRunning());
            infoMap.put("containersStopped", info.getContainersStopped());
            infoMap.put("images", info.getImages());
            infoMap.put("serverName", info.getName());
            result.put("dockerInfo", infoMap);
        } catch (Exception ignored) {
            // 获取 info 失败时不额外处理，Docker 状态已在上一步标记
        }

        return ResponseEntity.ok(result);
    }
}
