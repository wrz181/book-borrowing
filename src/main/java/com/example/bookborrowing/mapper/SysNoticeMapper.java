package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统公告 Mapper。
 */
@Mapper
public interface SysNoticeMapper {

    /** 新增公告。 */
    int insert(SysNotice notice);

    /** 查询全部公告（时间倒序）。 */
    List<SysNotice> selectAll();

    /** 查询最新一条公告。 */
    SysNotice selectLatest();

    /** 按ID查询公告。 */
    SysNotice selectById(Long id);

    /** 按ID逻辑删除公告。 */
    int deleteById(Long id);
}
