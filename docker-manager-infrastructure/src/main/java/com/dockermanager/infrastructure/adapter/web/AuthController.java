package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.port.inbound.AuthenticationPort;
import com.dockermanager.domain.util.ParamValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationPort authenticationPort;

    public AuthController(AuthenticationPort authenticationPort) {
        this.authenticationPort = authenticationPort;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String nickname = body.get("nickname");
            ParamValidator.requireLength(username, 3, 50, "用户名长度需在3-50之间");
            ParamValidator.requireMinLength(password, 6, "密码至少6位");
            ParamValidator.requireMaxLength(nickname, 100, "昵称最长100个字符");
            return ResponseEntity.ok(authenticationPort.register(username, password, nickname));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            ParamValidator.requireNotBlank(username, "请输入用户名");
            ParamValidator.requireNotBlank(password, "请输入密码");
            return ResponseEntity.ok(authenticationPort.login(username, password));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        return ResponseEntity.ok(authenticationPort.getProfile(authentication.getName()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody Map<String, String> body) {
        try {
            ParamValidator.requireNotBlank(body.get("oldPassword"), "请输入原密码");
            ParamValidator.requireMinLength(body.get("newPassword"), 6, "新密码至少6位");
            authenticationPort.changePassword(authentication.getName(), body.get("oldPassword"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        try {
            var profile = authenticationPort.getProfile(authentication.getName());
            return "ADMIN".equals(profile.get("role"));
        } catch { return false; }
    }

    @GetMapping("/admin/users")
    public ResponseEntity<?> listUsers(Authentication authentication) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        return ResponseEntity.ok(authenticationPort.listUsers());
    }

    @PostMapping("/admin/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        try {
            authenticationPort.toggleUserEnabled(id);
            return ResponseEntity.ok(Map.of("message", "操作成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deleteUser(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        try {
            authenticationPort.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(Authentication authentication, @PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!isAdmin(authentication)) return ResponseEntity.status(403).body(Map.of("error", "需要管理员权限"));
        try {
            ParamValidator.requireMinLength(body.get("newPassword"), 6, "新密码至少6位");
            authenticationPort.adminResetPassword(id, body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "密码重置成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
