package com.example.bookborrowing.common;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户权限缓存：「缓存键 -> 权限编码集合」，减少每次请求的数据库查询。
 * 键为用户维度（u:{userId}，全身份权限并集）或身份维度（u:{userId}:r:{roleId}，当前身份权限）。
 * 角色权限变更或账号角色变更时需调用 {@link #clear()} 失效。
 */
@Component
public class PermissionCache {

    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();

    /**
     * 读取缓存。
     *
     * @param key 缓存键
     * @return 权限集合，未缓存返回 null
     */
    public Set<String> get(String key) {
        return cache.get(key);
    }

    /**
     * 写入缓存。
     *
     * @param key   缓存键
     * @param perms 权限集合
     */
    public void put(String key, Set<String> perms) {
        cache.put(key, Collections.unmodifiableSet(perms));
    }

    /**
     * 清空全部缓存（角色权限变更时调用）。
     */
    public void clear() {
        cache.clear();
    }
}
