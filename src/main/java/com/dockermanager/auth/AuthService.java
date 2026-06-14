package com.dockermanager.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证业务逻辑服务。
 *
 * <p>封装所有与用户认证、授权和用户管理相关的业务操作：</p>
 * <ul>
 *   <li><b>用户注册</b>：创建新用户，BCrypt 加密密码，自动签发 JWT Token</li>
 *   <li><b>用户登录</b>：验证密码，检查账号状态，更新最后登录时间，签发 JWT Token</li>
 *   <li><b>个人信息</b>：查询当前登录用户的资料（用户名、昵称、角色、创建时间等）</li>
 *   <li><b>修改密码</b>：验证旧密码后更新为新密码</li>
 *   <li><b>默认用户初始化</b>：系统首次启动时创建 admin 管理员账号</li>
 *   <li><b>管理员功能</b>：用户列表、启用/禁用、删除、重置密码</li>
 * </ul>
 *
 * <p>密码存储使用 BCrypt 哈希，不保存明文。所有敏感操作通过
 * {@link IllegalArgumentException} 抛出业务异常，由 Controller 层统一处理。</p>
 *
 * @see AuthController REST API 入口
 * @see JwtService Token 生成服务
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 注册新用户。
     *
     * <p>注册成功后自动签发 JWT Token，前端无需再次调用登录接口。</p>
     *
     * @param username 用户名（必填，不可重复）
     * @param password 明文密码（至少 6 位）
     * @param nickname 昵称（可选，为空时使用用户名作为昵称）
     * @return 包含 token、username、nickname 的响应 Map
     * @throws IllegalArgumentException 用户名已存在
     */
    public Map<String, Object> register(String username, String password, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 构建新用户：密码 BCrypt 加密存储，默认角色为 USER
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname != null ? nickname : username)
                .role("USER")
                .build();

        userRepository.save(user);

        // 注册即登录：签发 JWT Token
        String token = jwtService.generateToken(username);
        return Map.of(
                "token", token,
                "username", username,
                "nickname", user.getNickname(),
                "message", "注册成功"
        );
    }

    /**
     * 用户登录。
     *
     * <p>验证流程：查找用户 → 校验密码 → 检查启用状态 → 更新最后登录时间 → 签发 Token</p>
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 包含 token、username、nickname 的响应 Map
     * @throws IllegalArgumentException 用户名/密码错误或账号被禁用
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        // BCrypt 密码比对（自动处理盐值和成本因子）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 检查账号是否被管理员禁用
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        // 记录最后登录时间（用于审计和管理员查看）
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(username);
        return Map.of(
                "token", token,
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "message", "登录成功"
        );
    }

    /**
     * 获取用户个人资料。
     *
     * @param username 用户名（从 JWT Token 中提取）
     * @return 包含 username、nickname、role、createdAt、lastLoginAt 的 Map
     * @throws IllegalArgumentException 用户不存在
     */
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

    /**
     * 修改密码。
     *
     * <p>需要先验证旧密码，确认操作者身份后再更新为新密码。</p>
     *
     * @param username    当前登录用户名
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文，至少 6 位）
     * @throws IllegalArgumentException 用户不存在或旧密码错误
     */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 初始化默认管理员用户。
     *
     * <p>仅在数据库中不存在任何用户时执行（首次启动），
     * 创建默认的 admin 账号供初始登录使用。</p>
     *
     * <p>默认凭据（生产环境应立即修改）：</p>
     * <ul>
     *   <li>用户名: admin</li>
     *   <li>密码: admin123</li>
     *   <li>角色: ADMIN</li>
     * </ul>
     */
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

    // ==================== 管理员用户管理功能 ====================

    /**
     * 获取所有用户列表（管理员功能）。
     *
     * <p>返回脱敏后的用户信息（不包含密码字段），
     * 用于管理员在用户管理页面查看和操作系统用户。</p>
     *
     * @return 用户信息列表，每项包含 id、username、nickname、role、enabled、createdAt、lastLoginAt
     */
    public List<Map<String, Object>> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "nickname", u.getNickname() != null ? u.getNickname() : "",
                        "role", u.getRole(),
                        "enabled", u.isEnabled(),
                        "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                        "lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : ""
                ))
                .collect(Collectors.toList());
    }

    /**
     * 切换用户启用/禁用状态（管理员功能）。
     *
     * <p>被禁用的用户即使持有有效 Token 也会被 JwtAuthenticationFilter 拒绝访问。</p>
     *
     * @param userId 目标用户 ID
     * @throws IllegalArgumentException 用户不存在
     */
    public void toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    /**
     * 删除用户（管理员功能）。
     *
     * @param userId 目标用户 ID
     * @throws IllegalArgumentException 用户不存在
     */
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }
        userRepository.deleteById(userId);
    }

    /**
     * 管理员重置用户密码（管理员功能）。
     *
     * <p>无需知道旧密码，直接设置新密码。用于用户忘记密码时管理员协助重置。</p>
     *
     * @param userId      目标用户 ID
     * @param newPassword 新密码（明文，至少 6 位）
     * @throws IllegalArgumentException 用户不存在或密码不符合要求
     */
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
