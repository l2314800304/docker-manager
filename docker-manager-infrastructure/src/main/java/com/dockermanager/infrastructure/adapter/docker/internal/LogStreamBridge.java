package com.dockermanager.infrastructure.adapter.docker.internal;

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

/**
 * 容器日志流桥接器。
 *
 * <p>提供容器日志的流式推送和历史日志查询能力。
 * 使用 Jackson ObjectMapper 将日志帧序列化为 JSON 格式。</p>
 */
@Component
public class LogStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(LogStreamBridge.class);
    private final DockerClientBridge dockerClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Closeable> activeStreams = new ConcurrentHashMap<>();

    public LogStreamBridge(DockerClientBridge dockerClient, ObjectMapper objectMapper) {
        this.dockerClient = dockerClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动日志流推送。
     *
     * <p>创建异步日志流，每次收到新日志帧时通过回调通知调用方。
     * 日志行以 JSON 格式推送（包含 streamType 和 line 字段）。</p>
     *
     * @param containerId 容器 ID
     * @param onLog       收到日志帧时的回调（JSON 格式字符串）
     * @param onError     发生错误时的回调（可选）
     * @param onComplete  流完成时的回调（可选）
     * @return 流 ID（用于通过 {@link #stopStream(String)} 关闭）
     */
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

        var cmd = dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(100)
                .withTimestamps(true);

        Closeable closeable = cmd.exec(callback);
        activeStreams.put(streamId, closeable);

        return streamId;
    }

    /**
     * 获取容器的历史日志（非流式，一次性返回）。
     *
     * <p>阻塞等待所有日志帧接收完毕后返回完整日志文本。</p>
     *
     * @param containerId 容器 ID
     * @param tail        返回最后 N 行日志
     * @param since       只返回此 Unix 时间戳之后的日志（null 表示不限制）
     * @return 完整的历史日志文本
     */
    public String getHistoryLogs(String containerId, int tail, Integer since) {
        StringBuilder logs = new StringBuilder();

        var cmd = dockerClient.logContainerCmd(containerId)
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

    /**
     * 停止指定的日志流。
     *
     * @param streamId 流 ID（由 {@link #streamLogs} 返回）
     */
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

    /**
     * 停止所有活跃的日志流（应用关闭时调用）。
     */
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
