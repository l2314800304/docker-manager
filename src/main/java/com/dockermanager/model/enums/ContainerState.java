package com.dockermanager.model.enums;

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

    public static ContainerState fromDockerState(String state) {
        if (state == null) return UNKNOWN;
        for (ContainerState cs : values()) {
            if (cs.dockerState.equalsIgnoreCase(state)) return cs;
        }
        return UNKNOWN;
    }
}
