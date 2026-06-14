package com.dockermanager.application.port.outbound;

import com.dockermanager.domain.entity.StatusRecord;
import java.util.List;

/**
 * 状态变更记录持久化出站端口。
 */
public interface StatusRecordRepositoryPort {
    StatusRecord save(StatusRecord record);
    List<StatusRecord> findByContainerId(String containerId, int limit);
    List<StatusRecord> findByProjectName(String projectName, int limit);
}
