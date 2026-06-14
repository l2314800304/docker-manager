package com.dockermanager.config;

import com.dockermanager.websocket.LogWebSocketHandler;
import com.dockermanager.websocket.StatsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册配置。
 *
 * <p>注册两个 WebSocket 端点，用于向前端实时推送数据：</p>
 * <ul>
 *   <li>{@code /ws/logs/{containerId}} — 容器日志实时流推送
 *       <br>前端连接后先接收最近 100 条历史日志，随后持续接收新日志</li>
 *   <li>{@code /ws/stats/{containerId}} — 容器资源使用统计实时推送
 *       <br>以约 1 秒/次的频率推送 CPU/内存/网络/IO 数据</li>
 * </ul>
 *
 * <p>WebSocket 连接无需 JWT 认证（已在 SecurityConfig 中配置 permitAll），
 * 因为 WebSocket 握手阶段不便传递 Authorization header。</p>
 *
 * @see LogWebSocketHandler 日志 WebSocket 处理器
 * @see StatsWebSocketHandler Stats WebSocket 处理器
 */
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

    /**
     * 注册 WebSocket 处理器到指定 URL 路径。
     *
     * <p>路径中的 {@code **} 用于匹配容器 ID 参数，
     * 处理器内部通过解析 URI 提取 containerId。</p>
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 日志流端点: /ws/logs/{containerId}
        registry.addHandler(logWebSocketHandler, "/ws/logs/**")
                .setAllowedOrigins("*");  // 允许所有来源（生产环境应限制具体域名）

        // 资源统计端点: /ws/stats/{containerId}
        registry.addHandler(statsWebSocketHandler, "/ws/stats/**")
                .setAllowedOrigins("*");
    }
}
