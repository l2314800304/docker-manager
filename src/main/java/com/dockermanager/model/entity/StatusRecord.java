package com.dockermanager.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "status_record")
public class StatusRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "container_id", nullable = false, length = 64)
    private String containerId;

    @Column(name = "container_name")
    private String containerName;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "old_state", length = 20)
    private String oldState;

    @Column(name = "new_state", nullable = false, length = 20)
    private String newState;

    @Column(length = 500)
    private String image;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
