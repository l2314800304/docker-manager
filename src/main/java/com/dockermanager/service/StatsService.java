package com.dockermanager.service;

import com.dockermanager.docker.ContainerStatsCollector;
import com.dockermanager.model.dto.ContainerStatsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private final ContainerStatsCollector statsCollector;
    private final ContainerService containerService;
    private final ExecutorService statsExecutor = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8));

    public StatsService(ContainerStatsCollector statsCollector, ContainerService containerService) {
        this.statsCollector = statsCollector;
        this.containerService = containerService;
    }

    public ContainerStatsDTO getStatsSnapshot(String containerId) {
        return statsCollector.getStatsSnapshot(containerId);
    }

    /**
     * Collect stats for all running containers in parallel.
     * Each container's stats collection is independent, so parallelizing
     * significantly reduces the total response time.
     */
    public List<ContainerStatsDTO> getAllStats() {
        var containers = containerService.getAllContainers();
        var runningContainers = containers.stream()
                .filter(c -> c.getState() == com.dockermanager.model.enums.ContainerState.RUNNING)
                .collect(Collectors.toList());

        if (runningContainers.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ContainerStatsDTO>> futures = runningContainers.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return statsCollector.getStatsSnapshot(c.getContainerId());
                    } catch (Exception e) {
                        log.debug("Failed to get stats for container {}: {}", c.getContainerId(), e.getMessage());
                        return null;
                    }
                }, statsExecutor))
                .collect(Collectors.toList());

        // Wait for all with timeout
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(ex -> null)
                .join();

        return futures.stream()
                .map(f -> f.getNow(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public ContainerStatsCollector getStatsCollector() {
        return statsCollector;
    }
}
