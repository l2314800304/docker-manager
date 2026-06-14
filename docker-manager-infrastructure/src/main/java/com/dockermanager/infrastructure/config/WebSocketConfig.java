package com.dockermanager.infrastructure.config;

import com.dockermanager.infrastructure.adapter.web.LogWebSocketHandler;
import com.dockermanager.infrastructure.adapter.web.StatsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 端点注册配置 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogWebSocketHandler logWebSocketHandler;
    private final StatsWebSocketHandler statsWebSocketHandler;

    public WebSocketConfig(LogWebSocketHandler logWebSocketHandler,
                           StatsWebSocketHandler statsWebSocketHandler) {
        this.logWebSocketHandler = logWebSocketHandler;
        this.statsWebSocketHandler = statsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs/**").setAllowedOrigins("*");
        registry.addHandler(statsWebSocketHandler, "/ws/stats/**").setAllowedOrigins("*");
    }
}
