package com.dockermanager.infrastructure.adapter.web;

import com.dockermanager.domain.port.inbound.AuthenticationPort;
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
            if (username == null || username.isBlank() || password == null || password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空，密码至少6位"));
            }
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
            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请输入用户名和密码"));
            }
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
            authenticationPort.adminResetPassword(id, body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "密码重置成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
