package com.example.bookborrowing.common;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 静态资源（JS/CSS）版本号：清理 JS 缓存时递增，
 * 前端据此刷新浏览器缓存并重新加载页面。
 */
@Component
public class StaticResourceVersion {

    private final AtomicLong version = new AtomicLong(System.currentTimeMillis());

    /**
     * 读取当前版本号。
     *
     * @return 版本号
     */
    public long current() {
        return version.get();
    }

    /**
     * 递增版本号（清理缓存）。
     *
     * @return 新版本号
     */
    public long bump() {
        return version.updateAndGet(v -> Math.max(System.currentTimeMillis(), v + 1));
    }
}
