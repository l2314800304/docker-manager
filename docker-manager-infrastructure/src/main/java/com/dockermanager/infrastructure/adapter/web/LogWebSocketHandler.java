package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.infrastructure.adapter.docker.internal.LogStreamBridge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class LogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LogWebSocketHandler.class);
    private final LogStreamBridge logStreamBridge;
    private final ObjectMapper objectMapper;
    private final Map<String, String> sessionToStream = new ConcurrentHashMap<>();

    public LogWebSocketHandler(LogStreamBridge logStreamBridge, ObjectMapper objectMapper) {
        this.logStreamBridge = logStreamBridge;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String containerId = extractContainerId(session, "/ws/logs/");
        if (containerId == null || containerId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("Log WebSocket connected for container: {}", containerId);

        // First send history logs
        String history = logStreamBridge.getHistoryLogs(containerId, 100, null);
        if (!history.isBlank()) {
            String[] lines = history.split("\n");
            for (String line : lines) {
                try {
                    String json = objectMapper.writeValueAsString(Map.of(
                            "streamType", "stdout",
                            "line", line
                    ));
                    session.sendMessage(new TextMessage(json));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize history line: {}", e.getMessage());
                }
            }
        }

        // Start streaming
        String streamId = logStreamBridge.streamLogs(containerId,
                json -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(json));
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send log message: {}", e.getMessage());
                    }
                },
                error -> {
                    try {
                        if (session.isOpen()) {
                            String json = objectMapper.writeValueAsString(Map.of(
                                    "streamType", "error",
                                    "line", error.getMessage() != null ? error.getMessage() : "Unknown error"
                            ));
                            session.sendMessage(new TextMessage(json));
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send error: {}", e.getMessage());
                    }
                },
                () -> {}
        );

        sessionToStream.put(session.getId(), streamId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String streamId = sessionToStream.remove(session.getId());
        if (streamId != null) {
            logStreamBridge.stopStream(streamId);
            log.info("Log WebSocket closed, stream stopped: {}", streamId);
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
