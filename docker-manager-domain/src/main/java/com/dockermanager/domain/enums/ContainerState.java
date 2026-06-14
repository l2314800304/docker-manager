package com.dockermanager.domain.enums;

/**
 * 容器运行状态枚举。映射 Docker API 返回的状态字符串到统一枚举值。
 */
public enum ContainerState {
    RUNNING("running"),
    STOPPED("exited"),
    PAUSED("paused"),
    RESTARTING("restarting"),
    DEAD("dead"),
    CREATED("created"),
    UNKNOWN("unknown");

    private final String dockerState;

    ContainerState(String dockerState) {
        this.dockerState = dockerState;
    }

    public String getDockerState() {
        return dockerState;
    }

    /** 将 Docker API 返回的状态字符串转换为枚举值 */
    public static ContainerState fromDockerState(String state) {
        if (state == null) return UNKNOWN;
        for (ContainerState cs : values()) {
            if (cs.dockerState.equalsIgnoreCase(state)) return cs;
        }
        return UNKNOWN;
    }
}
