package com.dockermanager.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户领域实体。
 *
 * <p>表示系统中的注册用户，支持角色区分（ADMIN/USER）和启用/禁用控制。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名（唯一约束） */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 加密后的密码 */
    @Column(nullable = false)
    private String password;

    /** 用户昵称（显示用） */
    @Column(length = 100)
    private String nickname;

    /** 角色: ADMIN（管理员）或 USER（普通用户） */
    @Column(length = 20)
    @Builder.Default
    private String role = "USER";

    /** 是否启用（禁用后即使 Token 有效也拒绝访问） */
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
