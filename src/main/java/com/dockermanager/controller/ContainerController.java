package com.dockermanager.controller;

import com.dockermanager.model.dto.ContainerInfoDTO;
import com.dockermanager.service.ContainerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 容器管理 REST API 控制器。
 *
 * <p>提供容器的查询和生命周期操作端点。所有端点需要 JWT 认证。</p>
 *
 * <h3>端点列表：</h3>
 * <ul>
 *   <li>{@code GET /api/containers} — 获取所有容器列表</li>
 *   <li>{@code GET /api/containers/{id}} — 获取单个容器详情</li>
 *   <li>{@code GET /api/containers/{id}/inspect} — Docker inspect 原始数据</li>
 *   <li>{@code POST /api/containers/{id}/restart} — 重启容器</li>
 *   <li>{@code POST /api/containers/{id}/stop} — 停止容器</li>
 *   <li>{@code POST /api/containers/{id}/start} — 启动容器</li>
 * </ul>
 *
 * <p>容器操作（重启/停止/启动）会同时记录审计日志和状态变更历史。</p>
 *
 * @see ContainerService 容器业务逻辑
 */
@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    /** 获取宿主机上所有容器的列表（含 Compose 和非 Compose 容器） */
    @GetMapping
    public List<ContainerInfoDTO> getAllContainers() {
        return containerService.getAllContainers();
    }

    /**
     * 获取指定容器的详细信息。
     *
     * <p>支持完整 ID 或 ID 前缀匹配（类似 docker CLI 的行为）。</p>
     *
     * @param id 容器 ID（完整或前缀）
     * @return 200 + 容器信息，或 404 未找到
     */
    @GetMapping("/{id}")
    public ResponseEntity<ContainerInfoDTO> getContainer(@PathVariable String id) {
        return containerService.getContainer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取容器的 Docker inspect 原始数据。
     *
     * <p>返回完整的 inspect 信息（配置、网络、挂载、状态等），
     * 用于高级调试场景。</p>
     *
     * @param id 容器 ID
     * @return 200 + inspect 数据，或 404 容器不存在
     */
    @GetMapping("/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectContainer(@PathVariable String id) {
        try {
            return ResponseEntity.ok(containerService.inspectContainer(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 重启容器。
     *
     * <p>操作成功后会记录审计日志和状态变更历史。
     * 后端发送重启指令后立即返回，容器实际重启需要几秒时间。</p>
     *
     * @param id 容器 ID
     * @return 200 + 成功消息，或 400 + 错误消息
     */
    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, Object>> restartContainer(@PathVariable String id) {
        try {
            containerService.restartContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container restarting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 停止容器。
     *
     * <p>Docker 会先发送 SIGTERM 信号，等待超时后发送 SIGKILL 强制终止。</p>
     *
     * @param id 容器 ID
     * @return 200 + 成功消息，或 400 + 错误消息
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopContainer(@PathVariable String id) {
        try {
            containerService.stopContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container stopping"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 启动已停止的容器。
     *
     * @param id 容器 ID
     * @return 200 + 成功消息，或 400 + 错误消息
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startContainer(@PathVariable String id) {
        try {
            containerService.startContainer(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Container starting"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
