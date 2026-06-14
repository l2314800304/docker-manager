package com.dockermanager.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class ContainerLogStreamer {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogStreamer.class);
    private final DockerClientService dockerClientService;
    private final ObjectMapper objectMapper;
    private final Map<String, Closeable> activeStreams = new ConcurrentHashMap<>();

    public ContainerLogStreamer(DockerClientService dockerClientService, ObjectMapper objectMapper) {
        this.dockerClientService = dockerClientService;
        this.objectMapper = objectMapper;
    }

    public String streamLogs(String containerId, Consumer<String> onLog, Consumer<Throwable> onError, Runnable onComplete) {
        String streamId = containerId + "-" + System.currentTimeMillis();

        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame != null && frame.getPayload() != null) {
                    String streamType = frame.getStreamType() == StreamType.STDERR ? "stderr" : "stdout";
                    String line = new String(frame.getPayload()).stripTrailing();
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                                "streamType", streamType,
                                "line", line
                        ));
                        onLog.accept(json);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize log line: {}", e.getMessage());
                    }
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Log stream error for {}: {}", containerId, throwable.getMessage());
                if (onError != null) onError.accept(throwable);
                activeStreams.remove(streamId);
            }

            @Override
            public void onComplete() {
                log.debug("Log stream completed for {}", containerId);
                if (onComplete != null) onComplete.run();
                activeStreams.remove(streamId);
            }
        };

        var cmd = dockerClientService.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(100)
                .withTimestamps(true);

        Closeable closeable = cmd.exec(callback);
        activeStreams.put(streamId, closeable);

        return streamId;
    }

    public String getHistoryLogs(String containerId, int tail, Integer since) {
        StringBuilder logs = new StringBuilder();

        var cmd = dockerClientService.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail);

        if (since != null) {
            cmd.withSince(since);
        }

        try {
            cmd.exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    if (frame != null && frame.getPayload() != null) {
                        logs.append(new String(frame.getPayload()));
                    }
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return logs.toString();
    }

    public void stopStream(String streamId) {
        Closeable stream = activeStreams.remove(streamId);
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                log.warn("Failed to close log stream {}: {}", streamId, e.getMessage());
            }
        }
    }

    public void stopAllStreams() {
        activeStreams.forEach((id, stream) -> {
            try {
                stream.close();
            } catch (IOException e) {
                log.warn("Failed to close stream {}: {}", id, e.getMessage());
            }
        });
        activeStreams.clear();
    }
}
