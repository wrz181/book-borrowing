package com.example.bookborrowing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 启动时数据库连接检查：连接失败时打印醒目的修复提示（不阻断启动，页面仍可访问）。
 */
@Component
public class DatabaseStartupChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupChecker.class);

    private final DataSource dataSource;

    public DatabaseStartupChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            log.info("数据库连接成功：{}", conn.getMetaData().getURL());
        } catch (Exception e) {
            log.error("=====================================================");
            log.error("数据库连接失败，请检查 src/main/resources/application.yml");
            log.error("  spring.datasource.username / spring.datasource.password");
            log.error("  是否为你本机 MySQL 的真实账号密码，以及数据库 bookborrowing 是否已通过");
            log.error("  sql/bookborrowing.sql 导入。");
            log.error("失败原因：{}：{}", e.getClass().getSimpleName(), e.getMessage());
            log.error("=====================================================");
        }
    }
}
