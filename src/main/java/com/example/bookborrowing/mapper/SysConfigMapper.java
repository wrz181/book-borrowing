package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置 Mapper（表 sys_config）：承载日志级别等运行时配置的持久化。
 */
@Mapper
public interface SysConfigMapper {

    /**
     * 按配置键前缀查询配置项（如 log.level. -> 各模块日志级别）。
     *
     * @param prefix 配置键前缀
     * @return 匹配的配置项列表，无匹配时为空列表
     */
    List<SysConfig> selectByKeyPrefix(@Param("prefix") String prefix);

    /**
     * 写入或更新一条配置（INSERT ... ON DUPLICATE KEY UPDATE，按 config_key 幂等）。
     *
     * @param config 配置项
     * @return 影响行数
     */
    int insertOrUpdate(SysConfig config);
}
