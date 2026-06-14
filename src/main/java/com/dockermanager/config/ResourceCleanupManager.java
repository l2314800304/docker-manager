package com.dockermanager.config;

import com.dockermanager.docker.ContainerLogStreamer;
import com.dockermanager.docker.ContainerStatsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用关闭时的 Docker 资源清理管理器。
 *
 * <p>监听 Spring 的 {@link ContextClosedEvent} 事件，在应用优雅关闭阶段
 * 自动停止所有活跃的日志流和 Stats 采集器，防止 Docker API 连接泄漏。</p>
 *
 * <h3>清理的资源类型：</h3>
 * <ul>
 *   <li><b>日志流</b> ({@link ContainerLogStreamer}) — 通过 docker-java 的
 *       follow-stream 建立的长连接，不关闭会持续占用 Docker daemon 资源</li>
 *   <li><b>Stats 采集器</b> ({@link ContainerStatsCollector}) — 通过 docker-java 的
 *       stats-stream 建立的长连接，同上</li>
 * </ul>
 *
 * <p>配合 {@code application.yml} 中的优雅关闭配置使用：</p>
 * <pre>
 * server:
 *   shutdown: graceful
 * spring:
 *   lifecycle:
 *     timeout-per-shutdown-phase: 30s
 * </pre>
 */
@Component
public class ResourceCleanupManager {

    private static final Logger log = LoggerFactory.getLogger(ResourceCleanupManager.class);
    private final ContainerLogStreamer logStreamer;
    private final ContainerStatsCollector statsCollector;

    public ResourceCleanupManager(ContainerLogStreamer logStreamer,
                                  ContainerStatsCollector statsCollector) {
        this.logStreamer = logStreamer;
        this.statsCollector = statsCollector;
    }

    /**
     * 应用关闭时清理所有 Docker 资源。
     *
     * <p>由 Spring 事件机制自动触发，无需手动调用。
     * 每个清理步骤独立 try-catch，确保一个失败不影响其他资源的清理。</p>
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        log.info("Application shutting down, cleaning up Docker resources...");

        // 步骤 1: 停止所有活跃的日志流
        try {
            logStreamer.stopAllStreams();
            log.info("All log streams stopped");
        } catch (Exception e) {
            log.warn("Error stopping log streams: {}", e.getMessage());
        }

        // 步骤 2: 停止所有活跃的 Stats 采集器
        try {
            statsCollector.stopAllCollectors();
            log.info("All stats collectors stopped");
        } catch (Exception e) {
            log.warn("Error stopping stats collectors: {}", e.getMessage());
        }

        log.info("Docker resource cleanup completed");
    }
}
