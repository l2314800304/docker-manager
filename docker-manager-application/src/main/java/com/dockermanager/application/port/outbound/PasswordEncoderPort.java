package com.dockermanager.application.port.outbound;

/**
 * 密码编码器出站端口。隔离密码加密实现（BCrypt）。
 */
public interface PasswordEncoderPort {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
