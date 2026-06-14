package com.dockermanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerStatsDTO {
    private String containerId;
    private LocalDateTime timestamp;
    private CpuStats cpu;
    private MemoryStats memory;
    private NetworkStats network;
    private BlockIOStats blockIO;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuStats {
        private double percent;
        private long totalUsage;
        private long systemUsage;
        private int onlineCpus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryStats {
        private long usage;
        private long limit;
        private double percent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkStats {
        private long rxBytes;
        private long txBytes;
        private long rxBytesPerSec;
        private long txBytesPerSec;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockIOStats {
        private long readBytes;
        private long writeBytes;
    }
}
