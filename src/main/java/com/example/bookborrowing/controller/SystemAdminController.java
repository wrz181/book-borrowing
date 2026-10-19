package com.example.bookborrowing.controller;

import com.example.bookborrowing.annotation.RequirePermission;
import com.example.bookborrowing.common.LogEntry;
import com.example.bookborrowing.common.LogStore;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.common.StaticResourceVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理员控制台接口：日志类型设置、内存监控、JS 缓存清理。
 * 三个功能分别对应 system:log / system:monitor / system:cache 权限点。
 */
@RestController
@RequestMapping("/api/admin/system")
public class SystemAdminController {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogStore logStore;

    private final StaticResourceVersion staticResourceVersion;

    public SystemAdminController(LogStore logStore, StaticResourceVersion staticResourceVersion) {
        this.logStore = logStore;
        this.staticResourceVersion = staticResourceVersion;
    }

    /**
     * 查询可配置的日志模块及当前级别。
     *
     * @return 模块列表
     */
    @GetMapping("/logs/modules")
    @RequirePermission("system:log")
    public Result<List<Map<String, Object>>> logModules() {
        List<Map<String, Object>> modules = logStore.listModules();
        log.info("查询日志模块配置列表：共 {} 个模块", modules.size());
        return Result.success(modules);
    }

    /**
     * 设置指定模块的日志级别（运行时生效）。
     *
     * @param body 请求体：module、level
     * @return 设置后的级别
     */
    @PostMapping("/logs/level")
    @RequirePermission("system:log")
    public Result<String> setLogLevel(@RequestBody Map<String, String> body) {
        String module = body.get("module");
        String level = body.get("level");
        log.info("设置日志级别：模块={}，目标级别={}", module, level);
        return Result.success(logStore.setLevel(module, level));
    }

    /**
     * 按模块与级别（>= minLevel）分页查询内存日志。
     *
     * @param module   模块编码（可空）
     * @param level    最低级别（可空，默认取该模块配置级别）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/logs")
    @RequirePermission("system:log")
    public Result<PageResult<LogEntry>> logs(@RequestParam(required = false) String module,
                                             @RequestParam(required = false) String level,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<LogEntry> page = logStore.query(module, level, pageNum, pageSize);
        log.info("查询内存日志：模块={}，级别下限={}，第 {} 页，每页 {} 条，命中 {} 条",
                module, level, pageNum, pageSize, page.getTotal());
        return Result.success(page);
    }

    /**
     * 清空内存日志缓冲。
     *
     * @return 空响应
     */
    @PostMapping("/logs/clear")
    @RequirePermission("system:log")
    public Result<Void> clearLogs() {
        logStore.clear();
        log.info("清空内存日志缓冲");
        return Result.success();
    }

    /**
     * 内存监控：堆/非堆、内存池、GC、线程与运行时信息快照。
     *
     * @return 监控数据
     */
    @GetMapping("/memory")
    @RequirePermission("system:monitor")
    public Result<Map<String, Object>> memory() {
        Map<String, Object> data = new LinkedHashMap<>();

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();

        Map<String, Object> heapMap = new LinkedHashMap<>();
        heapMap.put("used", heap.getUsed());
        heapMap.put("committed", heap.getCommitted());
        heapMap.put("max", heap.getMax());
        heapMap.put("usagePercent", percent(heap.getUsed(), heap.getMax()));
        data.put("heap", heapMap);

        Map<String, Object> nonHeapMap = new LinkedHashMap<>();
        nonHeapMap.put("used", nonHeap.getUsed());
        nonHeapMap.put("committed", nonHeap.getCommitted());
        nonHeapMap.put("max", nonHeap.getMax());
        data.put("nonHeap", nonHeapMap);

        List<Map<String, Object>> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", pool.getName());
            item.put("type", pool.getType().name());
            item.put("used", usage.getUsed());
            item.put("committed", usage.getCommitted());
            item.put("max", usage.getMax());
            pools.add(item);
        }
        data.put("pools", pools);

        List<Map<String, Object>> gcList = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", gc.getName());
            item.put("count", gc.getCollectionCount());
            item.put("time", gc.getCollectionTime());
            gcList.add(item);
        }
        data.put("gc", gcList);

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> runtimeMap = new LinkedHashMap<>();
        runtimeMap.put("jvmName", runtime.getVmName());
        runtimeMap.put("jvmVersion", runtime.getVmVersion());
        runtimeMap.put("uptimeMs", runtime.getUptime());
        runtimeMap.put("startTime", TIME_FORMATTER.format(LocalDateTime.now().minusNanos(runtime.getUptime() * 1_000_000)));
        runtimeMap.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        runtimeMap.put("threadCount", threads.getThreadCount());
        runtimeMap.put("peakThreadCount", threads.getPeakThreadCount());
        runtimeMap.put("daemonThreadCount", threads.getDaemonThreadCount());
        data.put("runtime", runtimeMap);

        data.put("serverTime", TIME_FORMATTER.format(LocalDateTime.now()));
        log.debug("查询内存监控快照：堆使用率={}%", heapMap.get("usagePercent"));
        return Result.success(data);
    }

    /**
     * 查询当前静态资源版本号。
     *
     * @return 版本号
     */
    @GetMapping("/cache/version")
    @RequirePermission("system:cache")
    public Result<Long> cacheVersion() {
        return Result.success(staticResourceVersion.current());
    }

    /**
     * 清理 JS 缓存：递增静态资源版本号，前端据此清空浏览器缓存并重新加载。
     *
     * @return 新版本号
     */
    @PostMapping("/cache/clear")
    @RequirePermission("system:cache")
    public Result<Long> clearCache() {
        Long version = staticResourceVersion.bump();
        log.info("清理 JS 缓存：静态资源版本号已更新为 {}", version);
        return Result.success(version);
    }

    /**
     * 计算使用率百分比（保留一位小数）。
     *
     * @param used 已用
     * @param max  上限
     * @return 百分比
     */
    private double percent(long used, long max) {
        if (max <= 0) {
            return 0d;
        }
        return Math.round(used * 1000.0 / max) / 10.0;
    }
}
