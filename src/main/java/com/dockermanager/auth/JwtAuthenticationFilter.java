package com.dockermanager.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>继承 {@link OncePerRequestFilter}，确保每个请求只执行一次。
 * 在 Spring Security 过滤链中位于 {@code UsernamePasswordAuthenticationFilter} 之前。</p>
 *
 * <h3>处理流程：</h3>
 * <ol>
 *   <li>从 HTTP 请求头 {@code Authorization} 中提取 Bearer Token</li>
 *   <li>调用 {@link JwtService#isTokenValid(String)} 验证 Token 签名和有效期</li>
 *   <li>从 Token 中提取用户名，查询数据库确认用户存在且未被禁用</li>
 *   <li>创建 {@link UsernamePasswordAuthenticationToken} 并设置到 {@link SecurityContextHolder}</li>
 *   <li>后续 Spring Security 授权检查将根据 SecurityContext 中的认证信息判断权限</li>
 * </ol>
 *
 * <h3>特殊情况处理：</h3>
 * <ul>
 *   <li>无 Authorization 头 → 跳过认证，由后续授权检查决定是否拒绝</li>
 *   <li>Token 无效/过期 → 跳过认证，返回 401</li>
 *   <li>用户不存在/已禁用 → 跳过认证，即使 Token 有效也拒绝访问</li>
 * </ul>
 *
 * @see JwtService JWT Token 验证服务
 * @see SecurityConfig 过滤器注册位置
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * 执行 JWT 认证逻辑。
     *
     * <p>认证成功后将 {@link UsernamePasswordAuthenticationToken} 设置到 SecurityContext，
     * 其中包含：</p>
     * <ul>
     *   <li><b>principal</b>: 用户名字符串</li>
     *   <li><b>credentials</b>: null（无状态模式不保存密码/Token）</li>
     *   <li><b>authorities</b>: 用户角色对应的权限列表（如 {@code ROLE_ADMIN}）</li>
     * </ul>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链（调用 doFilter 继续后续处理）
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 提取 Authorization 请求头
        String authHeader = request.getHeader("Authorization");

        // 仅处理 Bearer Token 格式的认证头
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);  // 去掉 "Bearer " 前缀

            // 验证 Token（签名 + 有效期）
            if (jwtService.isTokenValid(token)) {
                String username = jwtService.extractUsername(token);
                var user = userRepository.findByUsername(username);

                // 确认用户存在且账号未被禁用
                if (user.isPresent() && user.get().isEnabled()) {
                    // 创建认证对象：用户名 + 角色权限
                    // Spring Security 要求角色以 "ROLE_" 为前缀
                    var authentication = new UsernamePasswordAuthenticationToken(
                            username, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole()))
                    );
                    // 设置到 SecurityContext，供后续授权检查使用
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        // 无论认证成功与否，都继续过滤器链
        // 认证失败时 SecurityContext 为空，由 SecurityConfig 的授权规则决定是否拒绝
        filterChain.doFilter(request, response);
    }
}
