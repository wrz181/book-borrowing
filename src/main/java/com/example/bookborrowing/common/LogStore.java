package com.example.bookborrowing.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存日志仓库：以 Logback Appender 形式挂到 Root Logger，
 * 将日志按环形缓冲（默认 1000 条）暂存，供「日志类型设置」页面按模块与级别查询。
 * 同时支持运行时调整指定模块（Logger）的日志级别。
 */
@Component
public class LogStore extends AppenderBase<ILoggingEvent> {

    /** 环形缓冲容量 */
    private static final int CAPACITY = 1000;

    /** 未匹配到模块时的编码 */
    public static final String MODULE_OTHER = "OTHER";

    /** 可配置的模块：编码 -> Logger 名 */
    private static final Map<String, String> MODULE_LOGGER = new LinkedHashMap<>();

    /** 模块显示名 */
    private static final Map<String, String> MODULE_NAME = new LinkedHashMap<>();

    /** 未配置时的默认级别 */
    private static final String DEFAULT_LEVEL = "INFO";

    static {
        MODULE_LOGGER.put("BOOK", "com.example.bookborrowing.service.impl.BookServiceImpl");
        MODULE_LOGGER.put("CATEGORY", "com.example.bookborrowing.service.impl.BookCategoryServiceImpl");
        MODULE_LOGGER.put("BORROW", "com.example.bookborrowing.service.impl.BorrowRecordServiceImpl");
        MODULE_LOGGER.put("AUTH", "com.example.bookborrowing.controller.AuthController");
        MODULE_LOGGER.put("INTEGRATION", "com.example.bookborrowing.service.impl.ErpIntegrationServiceImpl");
        MODULE_LOGGER.put("SYSTEM", "com.example.bookborrowing.controller.SystemAdminController");

        MODULE_NAME.put("BOOK", "图书管理");
        MODULE_NAME.put("CATEGORY", "分类管理");
        MODULE_NAME.put("BORROW", "借阅管理");
        MODULE_NAME.put("AUTH", "登录认证");
        MODULE_NAME.put("INTEGRATION", "应用集成");
        MODULE_NAME.put("SYSTEM", "系统管理");
    }

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Deque<LogEntry> buffer = new ArrayDeque<>();

    /** 模块编码 -> 已配置级别 */
    private final Map<String, String> moduleLevels = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * 启动时挂载到 Root Logger。
     */
    @PostConstruct
    public void init() {
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        setContext(rootLogger.getLoggerContext());
        start();
        rootLogger.addAppender(this);
        MODULE_LOGGER.forEach((code, loggerName) -> moduleLevels.put(code, DEFAULT_LEVEL));
    }

    @Override
    protected void append(ILoggingEvent event) {
        String module = moduleOf(event.getLoggerName());
        LogEntry entry = new LogEntry();
        entry.setId(sequence.incrementAndGet());
        entry.setTime(TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        entry.setLevel(event.getLevel().toString());
        entry.setModule(module);
        entry.setModuleName(MODULE_NAME.getOrDefault(module, "其他"));
        entry.setLogger(abbreviate(event.getLoggerName()));
        entry.setMessage(event.getFormattedMessage());
        synchronized (buffer) {
            if (buffer.size() >= CAPACITY) {
                buffer.pollFirst();
            }
            buffer.addLast(entry);
        }
    }

    /**
     * 查询可配置模块及其当前级别。
     *
     * @return 模块列表
     */
    public List<Map<String, Object>> listModules() {
        List<Map<String, Object>> result = new ArrayList<>();
        MODULE_LOGGER.forEach((code, loggerName) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", code);
            item.put("moduleName", MODULE_NAME.getOrDefault(code, code));
            item.put("logger", loggerName);
            item.put("level", currentLevel(code));
            result.add(item);
        });
        return result;
    }

    /**
     * 运行时设置模块日志级别（同时作用于 Logback Logger 与本页展示阈值）。
     *
     * @param module 模块编码
     * @param level  级别（TRACE/DEBUG/INFO/WARN/ERROR）
     * @return 设置后的级别
     */
    public String setLevel(String module, String level) {
        String loggerName = MODULE_LOGGER.get(module);
        if (loggerName == null) {
            throw new BusinessException("未知的日志模块：" + module);
        }
        Level target = Level.toLevel(level, null);
        if (target == null) {
            throw new BusinessException("未知的日志级别：" + level);
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(loggerName).setLevel(target);
        moduleLevels.put(module, target.toString());
        return target.toString();
    }

    /**
     * 读取模块当前级别。
     *
     * @param module 模块编码
     * @return 级别
     */
    public String currentLevel(String module) {
        return moduleLevels.getOrDefault(module, DEFAULT_LEVEL);
    }

    /**
     * 分页查询日志（按级别 >= minLevel 过滤，可选按模块过滤），时间倒序。
     *
     * @param module   模块编码（可空）
     * @param minLevel 最低级别（可空，默认取该模块配置级别或 INFO）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<LogEntry> query(String module, String minLevel, int pageNum, int pageSize) {
        String effectiveLevel = (minLevel == null || minLevel.isBlank())
                ? (module == null || module.isBlank() ? DEFAULT_LEVEL : currentLevel(module))
                : minLevel;
        int threshold = Level.toLevel(effectiveLevel, Level.INFO).toInt();
        List<LogEntry> matched = new ArrayList<>();
        synchronized (buffer) {
            var it = buffer.descendingIterator();
            while (it.hasNext()) {
                LogEntry entry = it.next();
                if (module != null && !module.isBlank() && !module.equals(entry.getModule())) {
                    continue;
                }
                if (Level.toLevel(entry.getLevel(), Level.INFO).toInt() < threshold) {
                    continue;
                }
                matched.add(entry);
            }
        }
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 20 : pageSize;
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageResult<>(matched.subList(from, to), (long) matched.size(), page, size);
    }

    /**
     * 清空内存日志缓冲。
     */
    public void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    /**
     * 由 Logger 名推断所属模块。
     *
     * @param loggerName Logger 名
     * @return 模块编码
     */
    private String moduleOf(String loggerName) {
        for (Map.Entry<String, String> entry : MODULE_LOGGER.entrySet()) {
            if (loggerName.startsWith(entry.getValue())) {
                return entry.getKey();
            }
        }
        return MODULE_OTHER;
    }

    /**
     * 缩短 Logger 名，仅保留最后两段。
     *
     * @param loggerName Logger 名
     * @return 简写名
     */
    private String abbreviate(String loggerName) {
        String[] parts = loggerName.split("\\.");
        if (parts.length <= 2) {
            return loggerName;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
