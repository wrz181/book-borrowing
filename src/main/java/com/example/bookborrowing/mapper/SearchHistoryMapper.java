package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.SearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索历史 Mapper。
 */
@Mapper
public interface SearchHistoryMapper {

    /** 新增搜索记录。 */
    int insert(SearchHistory history);

    /** 查询用户最近的搜索记录（时间倒序，限条数）。 */
    List<SearchHistory> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
