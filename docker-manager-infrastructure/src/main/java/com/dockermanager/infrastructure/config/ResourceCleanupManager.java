package com.dockermanager.infrastructure.config;

import com.dockermanager.infrastructure.adapter.docker.internal.LogStreamBridge;
import com.dockermanager.infrastructure.adapter.docker.internal.StatsBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用关闭时的 Docker 资源清理管理器。
 */
@Component
public class ResourceCleanupManager {

    private static final Logger log = LoggerFactory.getLogger(ResourceCleanupManager.class);
    private final LogStreamBridge logStreamer;
    private final StatsBridge statsCollector;

    public ResourceCleanupManager(LogStreamBridge logStreamer, StatsBridge statsCollector) {
        this.logStreamer = logStreamer;
        this.statsCollector = statsCollector;
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        log.info("Application shutting down, cleaning up Docker resources...");
        try { logStreamer.stopAllStreams(); log.info("All log streams stopped"); }
        catch (Exception e) { log.warn("Error stopping log streams: {}", e.getMessage()); }
        try { statsCollector.stopAllCollectors(); log.info("All stats collectors stopped"); }
        catch (Exception e) { log.warn("Error stopping stats collectors: {}", e.getMessage()); }
        log.info("Docker resource cleanup completed");
    }
}
