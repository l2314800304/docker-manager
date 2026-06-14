package com.dockermanager.service;

import com.dockermanager.docker.ContainerLogStreamer;
import org.springframework.stereotype.Service;

@Service
public class LogService {

    private final ContainerLogStreamer logStreamer;

    public LogService(ContainerLogStreamer logStreamer) {
        this.logStreamer = logStreamer;
    }

    public String getLogs(String containerId, int tail, Integer since) {
        return logStreamer.getHistoryLogs(containerId, tail, since);
    }

    public ContainerLogStreamer getLogStreamer() {
        return logStreamer;
    }
}
