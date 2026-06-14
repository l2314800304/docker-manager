package com.dockermanager.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT (JSON Web Token) 服务。
 *
 * <p>提供 JWT Token 的生成、解析和验证功能，是系统无状态认证的核心组件。</p>
 *
 * <h3>Token 结构：</h3>
 * <ul>
 *   <li><b>Header</b>: {"alg": "HS256", "typ": "JWT"}</li>
 *   <li><b>Payload</b>: {"sub": "username", "username": "username", "iat": ..., "exp": ...}</li>
 *   <li><b>Signature</b>: HMAC-SHA256(header.payload, secret)</li>
 * </ul>
 *
 * <h3>配置项（application.yml）：</h3>
 * <ul>
 *   <li>{@code jwt.secret} — 签名密钥，至少 256 位（32 字节），生产环境务必修改</li>
 *   <li>{@code jwt.expiration} — Token 有效期（毫秒），默认 86400000（24 小时）</li>
 * </ul>
 *
 * @see JwtAuthenticationFilter 在请求链路中调用本服务验证 Token
 */
@Service
public class JwtService {

    /** JWT 签名密钥（HMAC-SHA256 要求至少 256 位） */
    @Value("${jwt.secret:docker-manager-secret-key-that-is-at-least-256-bits-long-for-hs256}")
    private String secret;

    /** Token 有效期（毫秒），默认 24 小时 */
    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * 根据密钥字符串生成 HMAC-SHA256 签名密钥对象。
     *
     * @return SecretKey 用于 JWT 签名和验证
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为指定用户生成 JWT Token。
     *
     * <p>Token 中包含用户名作为 subject 和自定义 claim，
     * 以及签发时间 (iat) 和过期时间 (exp)。</p>
     *
     * @param username 用户名（将作为 JWT 的 subject）
     * @return 紧凑格式的 JWT Token 字符串
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                                    // JWT 标准 subject 字段
                .claims(Map.of("username", username))                 // 自定义 claim
                .issuedAt(new Date())                                 // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expiration))  // 过期时间
                .signWith(getSigningKey())                            // HMAC-SHA256 签名
                .compact();                                           // 生成紧凑格式字符串
    }

    /**
     * 从 Token 中提取用户名。
     *
     * @param token JWT Token 字符串
     * @return 用户名（JWT subject 字段的值）
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）。
     *
     * @param token JWT Token 字符串
     * @return true=有效, false=无效（签名错误/已过期/格式错误）
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            // 检查过期时间：expiration 在当前时间之后表示未过期
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            // 签名验证失败、Token 格式错误等异常一律返回无效
            return false;
        }
    }

    /**
     * 解析 Token 并提取所有 Claims（Payload 部分）。
     *
     * <p>此方法会验证签名，签名不匹配时抛出异常。</p>
     *
     * @param token JWT Token 字符串
     * @return 解析后的 Claims 对象
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())      // 设置验证密钥
                .build()
                .parseSignedClaims(token)          // 解析并验证签名
                .getPayload();                     // 获取 Payload (Claims)
    }
}
