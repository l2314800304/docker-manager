package com.dockermanager.domain.enums;

/** 告警指标类型枚举 */
public enum AlertMetricType {
    HOST_CPU("宿主机CPU"), HOST_MEMORY("宿主机内存"), HOST_DISK("宿主机磁盘"),
    CONTAINER_CPU("容器CPU"), CONTAINER_MEMORY("容器内存"), CONTAINER_STOPPED("容器停止");

    private final String displayName;
    AlertMetricType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
