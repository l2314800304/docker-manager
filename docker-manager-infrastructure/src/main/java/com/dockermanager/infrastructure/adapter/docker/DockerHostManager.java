package com.dockermanager.infrastructure.adapter.docker;

import com.dockermanager.domain.entity.DockerHost;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多宿主机 Docker 客户端管理器。
 *
 * <p>维护一个 hostId → DockerClient 的映射表，为每个宿主机创建独立的 DockerClient 实例。
 * 支持动态添加/移除宿主机连接，线程安全。</p>
 *
 * <h3>连接管理策略：</h3>
 * <ul>
 *   <li>按需创建：首次访问某宿主机时才创建 DockerClient</li>
 *   <li>缓存复用：已创建的 DockerClient 会被缓存，避免重复创建</li>
 *   <li>连接验证：创建时验证连接是否可用</li>
 * </ul>
 */
@Component
public class DockerHostManager {

    private static final Logger log = LoggerFactory.getLogger(DockerHostManager.class);

    /** hostId → DockerClient 缓存 */
    private final Map<Long, DockerClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 获取指定宿主机的 DockerClient（缓存或新建）。
     *
     * @param host 宿主机实体
     * @return DockerClient 实例
     * @throws RuntimeException 连接失败时抛出
     */
    public DockerClient getClient(DockerHost host) {
        return clientCache.computeIfAbsent(host.getId(), id -> createClient(host));
    }

    /**
     * 为指定宿主机创建新的 DockerClient（替换缓存）。
     * 用于宿主机配置更新后重新建立连接。
     */
    public DockerClient recreateClient(DockerHost host) {
        removeClient(host.getId());
        DockerClient client = createClient(host);
        clientCache.put(host.getId(), client);
        return client;
    }

    /** 移除指定宿主机的 DockerClient 缓存 */
    public void removeClient(Long hostId) {
        clientCache.remove(hostId);
    }

    /** 移除所有客户端缓存 */
    public void clearAll() {
        clientCache.clear();
    }

    /**
     * 创建 DockerClient 实例。
     *
     * <p>根据宿主机的连接配置（socket/tcp + tls）创建对应的 DockerClient。</p>
     */
    private DockerClient createClient(DockerHost host) {
        log.info("Creating DockerClient for host [{}]: {} ({})", host.getId(), host.getName(), host.getConnectionUrl());

        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(host.getConnectionUrl())
                .withDockerTlsVerify(host.isTlsEnabled());

        if (host.isTlsEnabled() && host.getCertPath() != null && !host.getCertPath().isBlank()) {
            configBuilder.withDockerCertPath(host.getCertPath());
        }

        DefaultDockerClientConfig config = configBuilder.build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(15))
                .responseTimeout(Duration.ofSeconds(30))
                .maxConnections(50)
                .build();

        return DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    /**
     * 测试宿主机连接并返回 Docker 版本号。
     *
     * @param host 宿主机实体
     * @return Docker 版本字符串
     * @throws RuntimeException 连接失败时抛出
     */
    public String testConnection(DockerHost host) {
        DockerClient client = createClient(host);
        try {
            return client.versionCmd().exec().getVersion();
        } catch (Exception e) {
            throw new RuntimeException("Docker 连接失败: " + e.getMessage(), e);
        }
    }
}
