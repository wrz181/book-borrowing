package com.example.bookborrowing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 云计算扩展配置项（application.yml 中 app.* 前缀）：
 * Redis 热门排行/缓存/会话开关，HDFS 分布式归档开关与集群地址。
 * 两个开关均支持关闭：Redis 关闭后自动降级为数据库统计与纯 JWT 模式；
 * HDFS 关闭后归档接口返回友好提示，不影响其余业务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class CloudProps {

    /** Redis 扩展配置 */
    private final Redis redis = new Redis();

    /** HDFS 扩展配置 */
    private final Hdfs hdfs = new Hdfs();

    /** Redis 相关开关与连接参数 */
    @Data
    public static class Redis {

        /** 是否启用 Redis：热门排行/缓存/分布式会话 */
        private boolean enabled = true;
    }

    /** HDFS 相关开关与集群参数 */
    @Data
    public static class Hdfs {

        /** 是否启用 HDFS 归档功能 */
        private boolean enabled = false;

        /** NameNode RPC 地址（对应 core-site.xml 的 fs.defaultFS） */
        private String nameNode = "hdfs://localhost:9000";

        /** 操作 HDFS 的用户名（与集群超级用户一致，避免权限拒绝） */
        private String user = "root";

        /** 本项目在 HDFS 上的工作目录 */
        private String basePath = "/bookborrowing";
    }
}
