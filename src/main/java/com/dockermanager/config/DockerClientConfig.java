package com.dockermanager.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Docker 客户端连接配置。
 *
 * <p>根据 application.yml 中的 {@code docker.connection} 配置项，
 * 创建并注册一个全局的 {@link DockerClient} Bean。</p>
 *
 * <h3>支持的连接方式：</h3>
 * <ul>
 *   <li><b>Socket 模式</b>（默认）：直连 Docker daemon 的 Unix socket 或 Windows named pipe
 *       <ul>
 *         <li>Linux: {@code unix:///var/run/docker.sock}</li>
 *         <li>Windows: {@code npipe:////./pipe/docker_engine}</li>
 *       </ul>
 *   </li>
 *   <li><b>TCP 模式</b>：远程连接 Docker daemon（建议配合 TLS 使用）
 *       <ul><li>示例: {@code tcp://192.168.1.100:2375}</li></ul>
 *   </li>
 * </ul>
 *
 * <h3>HTTP 客户端参数：</h3>
 * <ul>
 *   <li>连接超时: 30 秒</li>
 *   <li>响应超时: 45 秒</li>
 *   <li>最大连接数: 100</li>
 * </ul>
 */
@Configuration
public class DockerClientConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerClientConfig.class);

    /** 连接方式: "socket" 或 "tcp" */
    @Value("${docker.connection.type:socket}")
    private String connectionType;

    /** Docker socket 路径（socket 模式使用） */
    @Value("${docker.connection.socket-path:npipe:////./pipe/docker_engine}")
    private String socketPath;

    /** Docker TCP 地址（tcp 模式使用） */
    @Value("${docker.connection.tcp-host:tcp://localhost:2375}")
    private String tcpHost;

    /** 是否启用 TLS 加密通信 */
    @Value("${docker.connection.tls-enabled:false}")
    private boolean tlsEnabled;

    /** TLS 证书目录路径（可选） */
    @Value("${docker.connection.cert-path:}")
    private String certPath;

    /**
     * 创建 Docker 客户端 Bean。
     *
     * <p>整个应用共享此单例客户端，所有 Docker API 调用都通过此实例进行。
     * 启动时会尝试连接 Docker daemon 并输出版本信息；连接失败时仅打印错误日志，
     * 不会阻止应用启动（允许后续通过健康检查发现连接问题）。</p>
     *
     * @return 配置好的 DockerClient 实例
     */
    @Bean
    public DockerClient dockerClient() {
        // 根据连接类型选择 Docker host 地址
        String dockerHost = "socket".equalsIgnoreCase(connectionType) ? socketPath : tcpHost;
        log.info("Connecting to Docker: type={}, host={}", connectionType, dockerHost);

        // 构建 Docker 客户端配置
        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(tlsEnabled);

        // 如果启用 TLS 且指定了证书路径，配置证书
        if (tlsEnabled && certPath != null && !certPath.isBlank()) {
            configBuilder.withDockerCertPath(certPath);
        }

        DefaultDockerClientConfig config = configBuilder.build();

        // 构建 Apache HttpClient5 作为底层 HTTP 传输层
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))   // TCP 连接建立超时
                .responseTimeout(Duration.ofSeconds(45))     // 等待响应数据超时
                .maxConnections(100)                         // HTTP 连接池大小
                .build();

        // 组装最终的 Docker 客户端
        DockerClient client = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        // 验证连接 - 调用 version API 确认 Docker daemon 可达
        try {
            String version = client.versionCmd().exec().getVersion();
            log.info("Docker connected successfully. Version: {}", version);
        } catch (Exception e) {
            log.error("Failed to connect to Docker: {}", e.getMessage());
        }

        return client;
    }
}
