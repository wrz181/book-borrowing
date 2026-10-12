package com.example.bookborrowing.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：token 生成、解析、过期校验。
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours:24}") long expireHours) {
        // HS256 要求密钥长度至少 256 位（32 字符）
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60 * 60 * 1000;
    }

    /**
     * 生成 JWT token。
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @return token 字符串
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token 中的用户ID。
     *
     * @param token token 字符串
     * @return 用户ID
     * @throws io.jsonwebtoken.JwtException token 非法或已过期时抛出
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        Object userId = claims.get("userId");
        return userId == null ? null : Long.valueOf(userId.toString());
    }
}
