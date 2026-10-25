package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.SearchHistory;

import java.util.List;

/**
 * 搜索历史业务接口。
 */
public interface SearchHistoryService {

    /**
     * 记录一次图书搜索（关键字去空格，空关键字忽略）。
     *
     * @param userId  搜索用户ID
     * @param keyword 搜索关键字
     */
    void record(Long userId, String keyword);

    /**
     * 查询用户最近的搜索记录。
     *
     * @param userId 用户ID
     * @return 最近 10 条搜索记录（时间倒序）
     */
    List<SearchHistory> myRecent(Long userId);
}
