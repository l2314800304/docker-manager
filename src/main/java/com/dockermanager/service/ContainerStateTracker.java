package com.dockermanager.service;

import com.dockermanager.model.entity.StatusRecord;
import com.dockermanager.repository.StatusRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tracks container state changes and persists them to the database.
 * Records transitions like RUNNING -> STOPPED, STOPPED -> RUNNING, etc.
 */
@Service
public class ContainerStateTracker {

    private static final Logger log = LoggerFactory.getLogger(ContainerStateTracker.class);
    private final StatusRecordRepository statusRecordRepository;

    public ContainerStateTracker(StatusRecordRepository statusRecordRepository) {
        this.statusRecordRepository = statusRecordRepository;
    }

    /**
     * Record a container state change.
     */
    public void recordStateChange(String containerId, String containerName,
                                  String projectName, String serviceName,
                                  String oldState, String newState,
                                  String image, String detail) {
        StatusRecord record = StatusRecord.builder()
                .containerId(containerId)
                .containerName(containerName)
                .projectName(projectName)
                .serviceName(serviceName)
                .oldState(oldState)
                .newState(newState)
                .image(image)
                .detail(detail)
                .build();

        try {
            statusRecordRepository.save(record);
            log.debug("Recorded state change: {} {} -> {} for {}",
                    serviceName != null ? serviceName : containerId, oldState, newState, projectName);
        } catch (Exception e) {
            log.warn("Failed to record state change: {}", e.getMessage());
        }
    }

    /**
     * Get recent state change records for a container.
     */
    public List<StatusRecord> getContainerHistory(String containerId, int limit) {
        return statusRecordRepository.findByContainerIdOrderByRecordedAtDesc(
                containerId, PageRequest.of(0, limit));
    }

    /**
     * Get recent state change records for a project.
     */
    public List<StatusRecord> getProjectHistory(String projectName, int limit) {
        return statusRecordRepository.findByProjectNameOrderByRecordedAtDesc(
                projectName, PageRequest.of(0, limit));
    }
}
