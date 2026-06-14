package com.dockermanager.application.port.outbound;

/**
 * JWT 令牌出站端口。隔离 JWT 生成/验证实现。
 */
public interface JwtPort {
    String generateToken(String username);
    String extractUsername(String token);
    boolean isTokenValid(String token);
}
