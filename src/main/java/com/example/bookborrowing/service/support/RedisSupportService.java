package com.example.bookborrowing.service.support;

import com.example.bookborrowing.config.CloudProps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis 支撑服务：热门排行（ZSet）、JSON 缓存（cache-aside）、登录会话（token 白名单）三块能力的统一入口。
 * <p>
 * 可用性设计：任何 Redis 操作失败（未启用 / 连接不上 / 超时）都不抛异常、不阻断业务，
 * 调用方按「缓存未命中 / 排行降级 / 会话退回纯 JWT」处理；
 * 连接失败后 30 秒内直接视为不可用（简易熔断），避免每个请求都等待超时。
 */
@Service
public class RedisSupportService {

    private static final Logger log = LoggerFactory.getLogger(RedisSupportService.class);

    /** 连接失败后的熔断时长：30 秒内不再尝试连接 */
    private static final long DOWN_MILLIS = 30_000L;

    /** 图书详情缓存 key 前缀：图书链路与借阅链路共用，保证借阅数据变化后能同步失效 */
    public static final String BOOK_DETAIL_CACHE_PREFIX = "bookborrowing:cache:book:detail:";

    private final StringRedisTemplate redisTemplate;

    private final CloudProps cloudProps;

    private final ObjectMapper objectMapper;

    /** Redis 不可用的熔断截止时间戳（毫秒），0 表示当前可用 */
    private volatile long downUntil = 0L;

    public RedisSupportService(StringRedisTemplate redisTemplate, CloudProps cloudProps, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.cloudProps = cloudProps;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 当前是否可用：开关关闭直接返回 false；连接异常则触发 30 秒熔断。
     *
     * @return 可用返回 true
     */
    public boolean isActive() {
        if (!cloudProps.getRedis().isEnabled()) {
            return false;
        }
        if (System.currentTimeMillis() < downUntil) {
            return false;
        }
        try {
            // execute 会负责连接的借还，ping 正常应答即认为可用（spring-data-redis 3.x 中 ping 返回 String）
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return pong != null && !pong.isEmpty();
        } catch (Exception e) {
            downUntil = System.currentTimeMillis() + DOWN_MILLIS;
            log.warn("Redis 不可用，30 秒内走降级逻辑：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 热门排行加分：ZSet INCRBY。
     *
     * @param key    排行 ZSet key
     * @param member 成员（图书ID字符串）
     * @param delta  加分值
     */
    public void incrScore(String key, String member, double delta) {
        if (!isActive()) {
            return;
        }
        try {
            redisTemplate.opsForZSet().incrementScore(key, member, delta);
        } catch (Exception e) {
            trip(e);
        }
    }

    /**
     * 取排行前 N 名（分数从高到低），保持名次顺序。
     *
     * @param key 排行 ZSet key
     * @param n   名次数
     * @return 有序 Map：成员（图书ID）→ 热度分；不可用或为空返回空 Map
     */
    public Map<String, Double> topScores(String key, long n) {
        if (!isActive()) {
            return Map.of();
        }
        try {
            var tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, Math.max(n, 1) - 1);
            Map<String, Double> result = new LinkedHashMap<>();
            if (tuples == null) {
                return result;
            }
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null && tuple.getScore() != null) {
                    result.put(tuple.getValue(), tuple.getScore());
                }
            }
            return result;
        } catch (Exception e) {
            trip(e);
            return Map.of();
        }
    }

    /**
     * 写入 JSON 缓存（cache-aside 的 put 步骤）。
     *
     * @param key   缓存 key
     * @param value 值对象
     * @param ttl   过期时间
     */
    public void cachePutJson(String key, Object value, Duration ttl) {
        if (!isActive()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            // 序列化失败属于数据问题而非 Redis 不可用，跳过缓存即可，不触发熔断
            log.warn("缓存序列化失败，本次跳过缓存：{}", e.getMessage());
        } catch (Exception e) {
            trip(e);
        }
    }

    /**
     * 读取 JSON 缓存（cache-aside 的 get 步骤）。
     *
     * @param key  缓存 key
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 命中返回反序列化对象，未命中或不可用返回 null
     */
    public <T> T cacheGetJson(String key, Class<T> type) {
        if (!isActive()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            // 反序列化失败属于数据问题而非 Redis 不可用，按未命中处理（回源查库），不触发熔断
            log.warn("缓存反序列化失败，按未命中处理：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            trip(e);
            return null;
        }
    }

    /**
     * 失效某本图书的详情缓存：图书详情缓存了「图书 + 一对多借阅记录」的聚合结构，
     * 因此除图书修改/删除外，借阅申请、审批、归还等借阅链路变化也必须同步失效，
     * 否则 5 分钟 TTL 内详情页会读到陈旧的借阅记录。
     *
     * @param bookId 图书ID
     */
    public void evictBookDetailCache(Long bookId) {
        if (bookId != null) {
            evict(BOOK_DETAIL_CACHE_PREFIX + bookId);
        }
    }

    /**
     * 删除缓存（图书更新/删除时失效）。
     *
     * @param key 缓存 key
     */
    public void evict(String key) {
        if (!isActive()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            trip(e);
        }
    }

    /**
     * 登录成功后保存 token 白名单：key=token，value=userId，TTL 与 JWT 有效期一致。
     *
     * @param token  JWT token
     * @param userId 用户ID
     * @param ttl    有效期
     */
    public void saveToken(String token, Long userId, Duration ttl) {
        if (!isActive()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(tokenKey(token), String.valueOf(userId), ttl);
        } catch (Exception e) {
            trip(e);
        }
    }

    /**
     * 校验 token 是否仍在线（登出后从白名单删除即失效，实现服务端可控的会话吊销）。
     * 命中时顺带续期（滑动过期）。Redis 不可用时返回 true，退回纯 JWT 校验。
     *
     * @param token  JWT token
     * @param userId token 解析出的用户ID
     * @return 在线返回 true
     */
    public boolean tokenAlive(String token, Long userId, Duration ttl) {
        if (!isActive()) {
            return true;
        }
        try {
            String key = tokenKey(token);
            String saved = redisTemplate.opsForValue().get(key);
            if (saved == null) {
                log.info("token 已在服务端吊销或过期：userId={}", userId);
                return false;
            }
            // 滑动续期：活跃用户不因 TTL 过期被登出
            redisTemplate.expire(key, ttl);
            return true;
        } catch (Exception e) {
            trip(e);
            return true;
        }
    }

    /**
     * 登出：从白名单删除 token。
     *
     * @param token JWT token
     */
    public void removeToken(String token) {
        if (!isActive()) {
            return;
        }
        try {
            redisTemplate.delete(tokenKey(token));
        } catch (Exception e) {
            trip(e);
        }
    }

    /**
     * 组装 token 白名单 key。
     *
     * @param token JWT token
     * @return Redis key
     */
    private String tokenKey(String token) {
        return "bookborrowing:auth:token:" + token;
    }

    /**
     * 触发熔断并记录原因。
     *
     * @param e 异常
     */
    private void trip(Exception e) {
        downUntil = System.currentTimeMillis() + DOWN_MILLIS;
        log.warn("Redis 操作失败，30 秒内走降级逻辑：{}", e.getMessage());
    }
}
