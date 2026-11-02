package com.example.bookborrowing.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis 连接工厂定制：关闭 Lettuce 共享原生连接（shareNativeConnection=false）。
 * <p>
 * 原因：共享连接一旦进入损坏状态（半开连接、命令无应答），所有操作都会持续超时并反复触发熔断，
 * 且无法自愈；改为每次操作使用独立短连接后，坏连接只影响单次操作，isActive() 的探测也始终真实。
 * 本系统为单机演示规模，连接建立开销（本机 ~1ms）可以忽略。
 */
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
        if (properties.getPassword() != null) {
            standalone.setPassword(RedisPassword.of(properties.getPassword()));
        }
        Duration commandTimeout = properties.getTimeout() == null ? Duration.ofSeconds(2) : properties.getTimeout();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                (RedisConfiguration) standalone, clientConfig);
        factory.setShareNativeConnection(false);
        return factory;
    }
}
