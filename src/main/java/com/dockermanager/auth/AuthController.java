package com.dockermanager.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证与用户管理 REST API 控制器。
 *
 * <p>提供用户认证和管理员用户管理的 HTTP 端点。所有端点位于 {@code /api/auth} 路径下。</p>
 *
 * <h3>公开端点（无需认证）：</h3>
 * <ul>
 *   <li>{@code POST /api/auth/register} — 用户注册</li>
 *   <li>{@code POST /api/auth/login} — 用户登录</li>
 * </ul>
 *
 * <h3>需认证端点：</h3>
 * <ul>
 *   <li>{@code GET /api/auth/profile} — 获取当前用户信息</li>
 *   <li>{@code POST /api/auth/change-password} — 修改密码</li>
 * </ul>
 *
 * <h3>管理员端点（需 ADMIN 角色，通过代码级权限检查）：</h3>
 * <ul>
 *   <li>{@code GET /api/auth/admin/users} — 获取所有用户列表</li>
 *   <li>{@code POST /api/auth/admin/users/{id}/toggle} — 启用/禁用用户</li>
 *   <li>{@code DELETE /api/auth/admin/users/{id}} — 删除用户</li>
 *   <li>{@code POST /api/auth/admin/users/{id}/reset-password} — 重置用户密码</li>
 * </ul>
 *
 * <p>管理员权限通过 {@link #isAdmin(Authentication)} 方法在代码层面检查，
 * 而非 Spring Security 的 {@code @PreAuthorize} 注解，以便更灵活地返回中文错误信息。</p>
 *
 * @see AuthService 认证业务逻辑
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册。
     *
     * <p>请求体示例：</p>
     * <pre>{"username": "user1", "password": "123456", "nickname": "用户一"}</pre>
     *
     * <p>成功响应包含 JWT Token，前端存储后可直接用于后续 API 调用。</p>
     *
     * @param body 请求体（username, password, nickname）
     * @return 200 + token 信息，或 400 + 错误消息
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String nickname = body.get("nickname");

            // 参数校验：用户名非空，密码至少 6 位
            if (username == null || username.isBlank() || password == null || password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空，密码至少6位"));
            }

            return ResponseEntity.ok(authService.register(username, password, nickname));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 用户登录。
     *
     * <p>请求体示例：</p>
     * <pre>{"username": "admin", "password": "admin123"}</pre>
     *
     * @param body 请求体（username, password）
     * @return 200 + token 信息，或 400 + 错误消息
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请输入用户名和密码"));
            }

            return ResponseEntity.ok(authService.login(username, password));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取当前登录用户的个人资料。
     *
     * <p>用户名从 JWT Token 中解析，由 Spring Security 注入到 Authentication 参数。</p>
     *
     * @param authentication Spring Security 认证信息（由 JwtAuthenticationFilter 设置）
     * @return 200 + 用户资料，或 401 未登录
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        return ResponseEntity.ok(authService.getProfile(authentication.getName()));
    }

    /**
     * 修改当前用户密码。
     *
     * <p>请求体示例：</p>
     * <pre>{"oldPassword": "admin123", "newPassword": "newpass123"}</pre>
     *
     * @param authentication 当前认证信息
     * @param body           请求体（oldPassword, newPassword）
     * @return 200 + 成功消息，或 400 + 错误消息
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @RequestBody Map<String, String> body) {
        try {
            authService.changePassword(
                    authentication.getName(),      // 从 JWT 提取的当前用户名
                    body.get("oldPassword"),
                    body.get("newPassword")
            );
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 管理员用户管理端点 ====================

    /**
     * 获取所有用户列表（仅管理员）。
     *
     * @return 200 + 用户列表，或 403 非管理员
     */
    @GetMapping("/admin/users")
    public ResponseEntity<?> listUsers(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        return ResponseEntity.ok(authService.listUsers());
    }

    /**
     * 切换用户启用/禁用状态（仅管理员）。
     *
     * @param authentication 当前认证信息
     * @param id             目标用户 ID
     * @return 200 + 成功消息，或 400/403 错误
     */
    @PostMapping("/admin/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        try {
            authService.toggleUserEnabled(id);
            return ResponseEntity.ok(Map.of("message", "操作成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除用户（仅管理员）。
     *
     * @param authentication 当前认证信息
     * @param id             目标用户 ID
     * @return 200 + 成功消息，或 400/403 错误
     */
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deleteUser(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        try {
            authService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 管理员重置用户密码（仅管理员）。
     *
     * <p>请求体示例：</p>
     * <pre>{"newPassword": "reset123"}</pre>
     *
     * @param authentication 当前认证信息
     * @param id             目标用户 ID
     * @param body           请求体（newPassword）
     * @return 200 + 成功消息，或 400/403 错误
     */
    @PostMapping("/admin/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(Authentication authentication,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        }
        try {
            authService.adminResetPassword(id, body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "密码重置成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 检查当前用户是否为管理员。
     *
     * <p>通过查询数据库中用户的 role 字段判断，而非依赖 SecurityContext 中的权限列表。
     * 这种方式更可靠，因为管理员可能在登录后被降级为普通用户。</p>
     *
     * @param authentication Spring Security 认证信息
     * @return true=管理员(ADMIN), false=非管理员或未认证
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        String username = authentication.getName();
        try {
            var profile = authService.getProfile(username);
            return "ADMIN".equals(profile.get("role"));
        } catch {
            return false;
        }
    }
}
