package com.dockermanager.infrastructure.adapter.persistence;

import com.dockermanager.application.port.outbound.StatusRecordRepositoryPort;
import com.dockermanager.domain.entity.StatusRecord;
import com.dockermanager.infrastructure.adapter.persistence.repository.JpaStatusRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 状态记录持久化适配器。实现应用层的 StatusRecordRepositoryPort 出站端口。
 */
@Component
public class StatusRecordRepositoryAdapter implements StatusRecordRepositoryPort {

    private final JpaStatusRecordRepository jpaStatusRecordRepository;

    public StatusRecordRepositoryAdapter(JpaStatusRecordRepository jpaStatusRecordRepository) {
        this.jpaStatusRecordRepository = jpaStatusRecordRepository;
    }

    @Override
    public StatusRecord save(StatusRecord record) {
        return jpaStatusRecordRepository.save(record);
    }

    @Override
    public List<StatusRecord> findByContainerId(String containerId, int limit) {
        return jpaStatusRecordRepository.findByContainerIdOrderByRecordedAtDesc(containerId, PageRequest.of(0, limit));
    }

    @Override
    public List<StatusRecord> findByProjectName(String projectName, int limit) {
        return jpaStatusRecordRepository.findByProjectNameOrderByRecordedAtDesc(projectName, PageRequest.of(0, limit));
    }
}
