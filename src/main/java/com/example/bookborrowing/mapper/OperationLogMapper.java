package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface OperationLogMapper {

    /** 新增操作日志。 */
    int insert(OperationLog log);

    /** 查询最近的操作日志（时间倒序，限条数）。 */
    List<OperationLog> selectRecent(@Param("limit") int limit);
}
