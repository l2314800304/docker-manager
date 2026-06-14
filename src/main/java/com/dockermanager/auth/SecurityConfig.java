package com.dockermanager.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 *
 * <p>核心安全策略：</p>
 * <ul>
 *   <li><b>无状态会话</b>：不使用 HttpSession，完全依赖 JWT Token 进行身份认证</li>
 *   <li><b>CSRF 禁用</b>：前后端分离架构 + JWT 认证不需要 CSRF 保护</li>
 *   <li><b>JWT 过滤器</b>：在 {@link UsernamePasswordAuthenticationFilter} 之前插入
 *       {@link JwtAuthenticationFilter}，从 Authorization header 提取并验证 JWT</li>
 * </ul>
 *
 * <h3>端点访问策略：</h3>
 * <table>
 *   <tr><td>{@code /api/auth/**}</td><td>公开 — 登录/注册端点</td></tr>
 *   <tr><td>{@code /api/health}</td><td>公开 — 健康检查（供 Docker HEALTHCHECK 使用）</td></tr>
 *   <tr><td>{@code /ws/**}</td><td>公开 — WebSocket 端点（握手不便传 JWT）</td></tr>
 *   <tr><td>{@code /h2-console/**}</td><td>公开 — H2 数据库控制台（生产环境应关闭）</td></tr>
 *   <tr><td>{@code /, /index.html, /assets/**}</td><td>公开 — 前端静态资源</td></tr>
 *   <tr><td>{@code /api/**}</td><td>需要认证 — 所有其他 API 端点</td></tr>
 * </table>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * 配置安全过滤链。
     *
     * <p>请求处理流程：</p>
     * <pre>
     * HTTP Request
     *   → CORS 处理
     *   → JwtAuthenticationFilter（提取并验证 JWT，设置 SecurityContext）
     *   → UsernamePasswordAuthenticationFilter（此处跳过，因为无状态）
     *   → 授权检查（根据 URL 路径匹配规则）
     *   → Controller
     * </pre>
     *
     * @param http HttpSecurity 构建器
     * @return 配置完成的安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())        // 前后端分离 + JWT，无需 CSRF token
            .cors(cors -> {})                     // CORS 在 WebConfig 中配置
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 不创建 HttpSession
            .authorizeHttpRequests(auth -> auth
                // === 公开端点（无需认证）===
                .requestMatchers("/api/auth/**").permitAll()       // 登录/注册/修改密码
                .requestMatchers("/api/health").permitAll()        // 系统健康检查
                .requestMatchers("/h2-console/**").permitAll()     // H2 数据库管理控制台
                .requestMatchers("/ws/**").permitAll()             // WebSocket 实时推送
                // === 前端静态资源 ===
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                // === 需要认证的端点 ===
                .requestMatchers("/api/**").authenticated()        // 所有其他 API 必须认证
                .anyRequest().permitAll()                          // 非 API 请求放行（SPA 路由转发等）
            )
            // 允许 H2 控制台使用 iframe（X-Frame-Options: SAMEORIGIN）
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            // 在默认的用户名密码认证过滤器之前插入 JWT 过滤器
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器 Bean。
     *
     * <p>使用 BCrypt 算法对密码进行哈希加密存储。
     * BCrypt 自带盐值和成本因子，安全性高且不易被彩虹表攻击。</p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
