package com.dockermanager.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局异常处理器。
 *
 * <p>使用 {@link RestControllerAdvice} 统一处理所有 Controller 中抛出的异常，
 * 将其转换为标准的 JSON 错误响应格式，避免 Spring 默认的错误页面（HTML）。</p>
 *
 * <h3>错误响应格式：</h3>
 * <pre>
 * {
 *   "error": "错误描述信息",
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 400  // 或 500
 * }
 * </pre>
 *
 * <h3>处理的异常类型：</h3>
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request（业务逻辑校验失败）</li>
 *   <li>{@link Exception} → 500 Internal Server Error（兜底处理未预期的异常）</li>
 * </ul>
 *
 * <p>所有 500 错误会记录完整的异常堆栈到日志，便于排查问题。
 * 400 错误仅返回错误消息，不记录堆栈（属于预期的业务异常）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理所有未捕获的异常（兜底处理器）。
     *
     * <p>记录完整异常堆栈并返回 500 响应。生产环境中应避免将内部错误细节
     * 暴露给前端，此处为开发便利性保留了错误消息。</p>
     *
     * @param e 未处理的异常
     * @return 500 + 错误 JSON
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", e.getMessage() != null ? e.getMessage() : "Internal error",
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 500
                ));
    }

    /**
     * 处理业务逻辑校验异常。
     *
     * <p>当 Service 层的业务逻辑校验失败时抛出（如"用户不存在"、"密码错误"等），
     * 返回 400 Bad Request。</p>
     *
     * @param e 业务校验异常
     * @return 400 + 错误 JSON
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "error", e.getMessage(),
                        "timestamp", LocalDateTime.now().toString(),
                        "status", 400
                ));
    }
}
