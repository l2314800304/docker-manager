package com.dockermanager;

import com.dockermanager.domain.port.inbound.AuthenticationPort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Docker Manager 应用入口。
 *
 * ComponentScan 扫描所有模块的包：
 * - infrastructure (适配器 + 配置)
 * - application (应用服务)
 * - domain (领域模型)
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.dockermanager")
@EntityScan(basePackages = "com.dockermanager.domain.entity")
@EnableJpaRepositories(basePackages = "com.dockermanager.infrastructure.adapter.persistence.repository")
public class DockerManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockerManagerApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDefaultAdmin(AuthenticationPort authenticationPort) {
        return args -> authenticationPort.initDefaultUser();
    }
}
