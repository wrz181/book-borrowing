package com.example.bookborrowing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体，对应表 sys_config（key/value 形式）。
 * 当前用于持久化「日志类型设置」页面的模块日志级别，配置键形如 log.level.BOOK。
 */
@Data
public class SysConfig {

    /** 配置键（主键），如 log.level.BOOK */
    private String configKey;

    /** 配置值，如 WARN */
    private String configValue;

    /** 备注说明 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
