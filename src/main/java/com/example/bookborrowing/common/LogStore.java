package com.example.bookborrowing.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.example.bookborrowing.entity.SysConfig;
import com.example.bookborrowing.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * 同时支持运行时调整指定模块（Logger）的日志级别，模块级别持久化到配置库 sys_config
 * （配置键 log.level.&lt;模块编码&gt;），重启后由 {@link #init()} 重新加载并应用。
 */
@Component
public class LogStore extends AppenderBase<ILoggingEvent> {

    private static final Logger log = LoggerFactory.getLogger(LogStore.class);

    /** 环形缓冲容量 */
    private static final int CAPACITY = 1000;

    /** 模块日志级别的配置库键前缀，完整键形如 log.level.BOOK */
    private static final String CONFIG_KEY_PREFIX = "log.level.";

    /** 写入配置库时的备注说明 */
    private static final String CONFIG_REMARK = "日志级别（按业务模块）";

    /** 未匹配到模块时的编码，同时是可配置/可筛选的合法模块键 */
    public static final String MODULE_OTHER = "OTHER";

    /** OTHER 分组不映射到单一 Logger：它汇集所有未匹配到业务模块的 Logger（含第三方框架） */
    private static final String OTHER_LOGGER_LABEL = "（未映射到业务模块的其余 Logger）";

    /** 可配置的模块：编码 -> Logger 名 */
    private static final Map<String, String> MODULE_LOGGER = new LinkedHashMap<>();

    /** 模块显示名 */
    private static final Map<String, String> MODULE_NAME = new LinkedHashMap<>();

    /** 未配置时的默认级别 */
    private static final String DEFAULT_LEVEL = "INFO";

    static {
        MODULE_LOGGER.put("BOOK", "com.example.bookborrowing.service.impl.BookServiceImpl");
        MODULE_LOGGER.put("CATEGORY", "com.example.bookborrowing.service.BookCategoryServiceImpl");
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

    /** 配置库 Mapper（延迟取用：取不到时降级为纯内存行为） */
    private final ObjectProvider<SysConfigMapper> sysConfigMapperProvider;

    private final AtomicLong sequence = new AtomicLong(0);

    public LogStore(ObjectProvider<SysConfigMapper> sysConfigMapperProvider) {
        this.sysConfigMapperProvider = sysConfigMapperProvider;
    }

    /**
     * 启动时挂载到 Root Logger，并从配置库恢复各模块日志级别。
     */
    @PostConstruct
    public void init() {
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        setContext(rootLogger.getLoggerContext());
        start();
        rootLogger.addAppender(this);
        MODULE_LOGGER.forEach((code, loggerName) -> moduleLevels.put(code, DEFAULT_LEVEL));
        // OTHER 分组同样纳入级别配置，默认为 INFO
        moduleLevels.put(MODULE_OTHER, DEFAULT_LEVEL);
        // 配置库中有值则以库为准，无值保持上面的 INFO 默认
        loadPersistedLevels();
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
     * 6 个业务模块之外追加 OTHER 分组（未映射到业务模块的日志），使其可配置、可筛选。
     *
     * @return 模块列表
     */
    public List<Map<String, Object>> listModules() {
        List<Map<String, Object>> result = new ArrayList<>();
        MODULE_LOGGER.forEach((code, loggerName) -> result.add(moduleItem(code, loggerName)));
        result.add(moduleItem(MODULE_OTHER, OTHER_LOGGER_LABEL));
        return result;
    }

    /**
     * 组装单个模块描述。
     *
     * @param code       模块编码
     * @param loggerName Logger 名
     * @return 模块描述
     */
    private Map<String, Object> moduleItem(String code, String loggerName) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("module", code);
        item.put("moduleName", MODULE_NAME.getOrDefault(code, "其他"));
        item.put("logger", loggerName);
        item.put("level", currentLevel(code));
        return item;
    }

    /**
     * 运行时设置模块日志级别（同时作用于 Logback Logger 与本页展示阈值）。
     * 级别先写入配置库 sys_config（写失败仅告警，不阻断运行时生效），再更新内存与 Logger 级别。
     * OTHER 分组汇集多个 Logger，无法由单一 Logger 承载运行级别，故只登记为「其他」分组的
     * 查询阈值（即下方日志按「级别 ≥ 所选级别」过滤的下限），不做 Logger 级别改写。
     *
     * @param module 模块编码
     * @param level  级别（TRACE/DEBUG/INFO/WARN/ERROR）
     * @return 设置后的级别
     */
    public String setLevel(String module, String level) {
        String loggerName = MODULE_LOGGER.get(module);
        boolean other = MODULE_OTHER.equals(module);
        if (loggerName == null && !other) {
            throw new BusinessException("未知的日志模块：" + module);
        }
        Level target = Level.toLevel(level, null);
        if (target == null) {
            throw new BusinessException("未知的日志级别：" + level);
        }
        // 落库（失败降级为内存生效）
        persistLevel(module, target.toString());
        if (!other) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(loggerName).setLevel(target);
        }
        moduleLevels.put(module, target.toString());
        return target.toString();
    }

    /**
     * 启动时从配置库加载各模块日志级别：有则用之、无则保持 INFO，随后应用到对应 Logger。
     * 读失败或配置表不存在时降级为原有内存行为（各模块 INFO），仅打 warn 日志，不阻断启动。
     */
    private void loadPersistedLevels() {
        List<SysConfig> rows;
        try {
            SysConfigMapper mapper = sysConfigMapperProvider.getIfAvailable();
            if (mapper == null) {
                log.warn("配置库 Mapper 不可用，日志级别降级为内存默认值（重启后不保留自定义级别）");
                return;
            }
            rows = mapper.selectByKeyPrefix(CONFIG_KEY_PREFIX);
        } catch (Exception e) {
            log.warn("从配置库加载日志级别失败，降级为内存默认值（重启后不保留自定义级别）：{}", e.getMessage());
            return;
        }
        int loaded = 0;
        for (SysConfig row : rows == null ? List.<SysConfig>of() : rows) {
            if (row == null || row.getConfigKey() == null) {
                continue;
            }
            String module = row.getConfigKey().substring(CONFIG_KEY_PREFIX.length());
            if (!moduleLevels.containsKey(module)) {
                log.warn("配置库存在未知日志模块键：{}，已忽略", row.getConfigKey());
                continue;
            }
            Level target = Level.toLevel(row.getConfigValue(), null);
            if (target == null) {
                log.warn("配置库中模块 {} 的级别取值无效：{}，保持默认 {}", module, row.getConfigValue(), DEFAULT_LEVEL);
                continue;
            }
            moduleLevels.put(module, target.toString());
            loaded++;
        }
        // 重启后运行时 Logger 级别也需要被应用，否则内存阈值与 Logger 实际输出不一致
        applyLoggerLevels();
        log.info("已从配置库加载日志级别：命中 {} 项，其余模块按 {} 处理", loaded, DEFAULT_LEVEL);
    }

    /**
     * 把内存中的模块级别应用到 6 个业务模块对应的 Logback Logger（OTHER 分组不映射单一 Logger）。
     */
    private void applyLoggerLevels() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        MODULE_LOGGER.forEach((code, loggerName) ->
                context.getLogger(loggerName).setLevel(Level.toLevel(currentLevel(code), Level.INFO)));
    }

    /**
     * 把模块级别写入配置库（按 config_key 幂等插入/更新）。
     * 写失败只打 warn 并保留内存级别，避免因配置库异常导致设置级别接口不可用。
     *
     * @param module 模块编码
     * @param level  级别
     */
    private void persistLevel(String module, String level) {
        try {
            SysConfigMapper mapper = sysConfigMapperProvider.getIfAvailable();
            if (mapper == null) {
                log.warn("配置库 Mapper 不可用，模块 {} 的级别仅内存生效（重启后丢失）", module);
                return;
            }
            SysConfig config = new SysConfig();
            config.setConfigKey(CONFIG_KEY_PREFIX + module);
            config.setConfigValue(level);
            config.setRemark(CONFIG_REMARK);
            mapper.insertOrUpdate(config);
        } catch (Exception e) {
            log.warn("日志级别持久化失败，模块 {} 的级别仅内存生效（重启后丢失）：{}", module, e.getMessage());
        }
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
