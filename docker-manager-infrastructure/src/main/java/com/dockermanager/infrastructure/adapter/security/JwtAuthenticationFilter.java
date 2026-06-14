package com.dockermanager.infrastructure.adapter.security;

import com.dockermanager.application.port.outbound.JwtPort;
import com.dockermanager.application.port.outbound.UserRepositoryPort;
import com.dockermanager.domain.entity.User;
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
import java.util.Optional;

/**
 * JWT 认证过滤器。从 Authorization header 提取 Bearer Token 并验证。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtPort jwtPort;
    private final UserRepositoryPort userRepository;

    public JwtAuthenticationFilter(JwtPort jwtPort, UserRepositoryPort userRepository) {
        this.jwtPort = jwtPort;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtPort.isTokenValid(token)) {
                String username = jwtPort.extractUsername(token);
                Optional<User> user = userRepository.findByUsername(username);

                if (user.isPresent() && user.get().isEnabled()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            username, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
