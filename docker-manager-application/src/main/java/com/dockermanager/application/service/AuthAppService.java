package com.dockermanager.application.service;

import com.dockermanager.application.port.outbound.JwtPort;
import com.dockermanager.application.port.outbound.PasswordEncoderPort;
import com.dockermanager.application.port.outbound.UserRepositoryPort;
import com.dockermanager.domain.entity.User;
import com.dockermanager.domain.port.inbound.AuthenticationPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证应用服务。实现 AuthenticationPort 入站端口。
 */
@Service
public class AuthAppService implements AuthenticationPort {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final JwtPort jwtPort;

    public AuthAppService(UserRepositoryPort userRepository,
                          PasswordEncoderPort passwordEncoder,
                          JwtPort jwtPort) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtPort = jwtPort;
    }

    @Override
    public Map<String, Object> register(String username, String password, String nickname) {
        ParamValidator.requireLength(username, 3, 50, "用户名长度需在3-50之间");
        ParamValidator.requireMinLength(password, 6, "密码至少6位");
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname != null ? nickname : username)
                .role("USER")
                .build();
        userRepository.save(user);
        String token = jwtPort.generateToken(username);
        return Map.of("token", token, "username", username, "nickname", user.getNickname(), "message", "注册成功");
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtPort.generateToken(username);
        return Map.of("token", token, "username", user.getUsername(), "nickname", user.getNickname(), "message", "登录成功");
    }

    @Override
    public Map<String, Object> getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return Map.of(
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "createdAt", user.getCreatedAt().toString(),
                "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : ""
        );
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        ParamValidator.requireNotBlank(oldPassword, "请输入原密码");
        ParamValidator.requireMinLength(newPassword, 6, "新密码至少6位");
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public void initDefaultUser() {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .nickname("Administrator")
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
        }
    }

    @Override
    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(), "username", u.getUsername(),
                        "nickname", u.getNickname() != null ? u.getNickname() : "",
                        "role", u.getRole(), "enabled", u.isEnabled(),
                        "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                        "lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : ""
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }
        userRepository.deleteById(userId);
    }

    @Override
    public void adminResetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("密码至少6位");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
