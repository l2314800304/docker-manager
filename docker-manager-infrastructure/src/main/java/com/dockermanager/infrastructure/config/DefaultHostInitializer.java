package com.dockermanager.infrastructure.config;

import com.dockermanager.application.port.outbound.HostRepositoryPort;
import com.dockermanager.domain.entity.DockerHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认宿主机初始化器。
 *
 * <p>在应用启动时检查数据库中是否存在宿主机记录。
 * 如果为空，则根据 application.yml 中的 {@code docker.connection} 配置
 * 自动注册一个默认宿主机，确保首次启动即可使用。</p>
 *
 * <p>使用 {@code @Order(1)} 确保在其他 CommandLineRunner（如默认管理员初始化）之前执行。</p>
 */
@Component
@Order(1)
public class DefaultHostInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultHostInitializer.class);

    private final HostRepositoryPort hostRepository;

    @Value("${docker.connection.type:socket}")
    private String connectionType;

    @Value("${docker.connection.socket-path:npipe:////./pipe/docker_engine}")
    private String socketPath;

    @Value("${docker.connection.tcp-host:tcp://localhost:2375}")
    private String tcpHost;

    @Value("${docker.connection.tls-enabled:false}")
    private boolean tlsEnabled;

    @Value("${docker.connection.cert-path:}")
    private String certPath;

    @Value("${docker.default-host-name:本地Docker}")
    private String defaultHostName;

    public DefaultHostInitializer(HostRepositoryPort hostRepository) {
        this.hostRepository = hostRepository;
    }

    @Override
    public void run(String... args) {
        if (hostRepository.findAll().isEmpty()) {
            // 根据连接类型选择连接地址
            String connectionUrl = "socket".equalsIgnoreCase(connectionType) ? socketPath : tcpHost;

            DockerHost defaultHost = DockerHost.builder()
                    .name(defaultHostName)
                    .connectionType(connectionType.toUpperCase())
                    .connectionUrl(connectionUrl)
                    .tlsEnabled(tlsEnabled)
                    .certPath(certPath)
                    .enabled(true)
                    .status("UNKNOWN")
                    .build();

            hostRepository.save(defaultHost);
            log.info("Registered default Docker host: {} ({})", defaultHostName, connectionUrl);
        } else {
            log.info("Docker hosts already exist in database, skipping default host initialization");
        }
    }
}
