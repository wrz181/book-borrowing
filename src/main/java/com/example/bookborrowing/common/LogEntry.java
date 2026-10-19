package com.example.bookborrowing.common;

import lombok.Data;

/**
 * 内存日志条目：供「日志类型设置」页面展示。
 */
@Data
public class LogEntry {

    /** 自增序号 */
    private long id;

    /** 记录时间（yyyy-MM-dd HH:mm:ss.SSS） */
    private String time;

    /** 级别：TRACE/DEBUG/INFO/WARN/ERROR */
    private String level;

    /** 所属模块编码 */
    private String module;

    /** 所属模块名称 */
    private String moduleName;

    /** 日志来源 Logger 名 */
    private String logger;

    /** 日志内容 */
    private String message;
}
