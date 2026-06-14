package com.dockermanager.websocket;

import com.dockermanager.docker.ContainerStatsCollector;
import com.dockermanager.model.dto.ContainerStatsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StatsWebSocketHandler.class);
    private final ContainerStatsCollector statsCollector;
    private final Map<String, String> sessionToCollector = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public StatsWebSocketHandler(ContainerStatsCollector statsCollector) {
        this.statsCollector = statsCollector;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String containerId = extractContainerId(session, "/ws/stats/");
        if (containerId == null || containerId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("Stats WebSocket connected for container: {}", containerId);

        String collectorId = statsCollector.startCollecting(containerId, stats -> {
            try {
                if (session.isOpen()) {
                    String json = objectMapper.writeValueAsString(stats);
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.warn("Failed to send stats: {}", e.getMessage());
            }
        });

        sessionToCollector.put(session.getId(), collectorId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String collectorId = sessionToCollector.remove(session.getId());
        if (collectorId != null) {
            statsCollector.stopCollector(collectorId);
            log.info("Stats WebSocket closed, collector stopped: {}", collectorId);
        }
    }

    private String extractContainerId(WebSocketSession session, String prefix) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        int idx = path.indexOf(prefix);
        if (idx >= 0) {
            return path.substring(idx + prefix.length());
        }
        return null;
    }
}
