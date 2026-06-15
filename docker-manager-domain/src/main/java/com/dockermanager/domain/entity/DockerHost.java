package com.dockermanager.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Docker 宿主机实体。表示一个被监控的 Docker 宿主机，包含连接信息和状态。
 * 系统支持同时对接多个宿主机，每个宿主机有独立的 DockerClient 实例。
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "docker_host")
public class DockerHost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "connection_type", nullable = false, length = 20) private String connectionType;
    @Column(name = "connection_url", nullable = false, length = 500) private String connectionUrl;
    @Builder.Default @Column(name = "tls_enabled") private boolean tlsEnabled = false;
    @Column(name = "cert_path", length = 500) private String certPath;
    @Builder.Default private boolean enabled = true;
    @Builder.Default @Column(length = 20) private String status = "UNKNOWN";
    @Column(name = "last_connected_at") private LocalDateTime lastConnectedAt;
    @Column(name = "docker_version", length = 50) private String dockerVersion;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
