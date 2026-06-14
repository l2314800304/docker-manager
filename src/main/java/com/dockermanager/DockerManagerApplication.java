package com.dockermanager;

import com.dockermanager.auth.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Docker Manager 应用入口类。
 *
 * <p>本系统是一个 Docker Compose 运行监控平台，提供以下核心能力：</p>
 * <ul>
 *   <li>自动发现宿主机上所有 docker-compose 项目及其服务</li>
 *   <li>实时日志流推送（WebSocket）和资源监控（CPU/内存/网络）</li>
 *   <li>容器生命周期管理（启动/停止/重启/镜像更新）</li>
 *   <li>容器内文件系统浏览</li>
 *   <li>JWT 认证 + 用户管理 + 操作审计</li>
 * </ul>
 *
 * <p>前端 Vue3 SPA 构建后嵌入 JAR 的 {@code /static/} 目录，实现单文件部署。</p>
 *
 * @see com.dockermanager.config.DockerClientConfig Docker 连接配置
 * @see com.dockermanager.auth.SecurityConfig 安全配置
 * @see com.dockermanager.config.WebConfig SPA 路由转发配置
 */
@SpringBootApplication
@EnableScheduling  // 启用定时任务支持（用于周期性数据采集等）
public class DockerManagerApplication {

    /**
     * Spring Boot 应用启动入口。
     *
     * @param args 命令行参数（支持通过 --key=value 覆盖 application.yml 配置）
     */
    public static void main(String[] args) {
        SpringApplication.run(DockerManagerApplication.class, args);
    }

    /**
     * 应用启动后自动初始化默认管理员用户。
     *
     * <p>当数据库中不存在任何用户时，创建默认的 admin 账号
     * （用户名: admin, 密码: admin123, 角色: ADMIN）。</p>
     *
     * @param authService 认证服务，提供用户初始化逻辑
     * @return CommandLineRunner 启动后执行的一次性任务
     */
    @Bean
    public CommandLineRunner initDefaultAdmin(AuthService authService) {
        return args -> authService.initDefaultUser();
    }
}
