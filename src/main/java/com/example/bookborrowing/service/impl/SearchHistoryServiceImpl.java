package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.entity.SearchHistory;
import com.example.bookborrowing.mapper.SearchHistoryMapper;
import com.example.bookborrowing.service.SearchHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索历史业务实现：图书动态查询时自动记录搜索关键字。
 */
@Service
public class SearchHistoryServiceImpl implements SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryServiceImpl.class);

    private final SearchHistoryMapper searchHistoryMapper;

    public SearchHistoryServiceImpl(SearchHistoryMapper searchHistoryMapper) {
        this.searchHistoryMapper = searchHistoryMapper;
    }

    @Override
    public void record(Long userId, String keyword) {
        if (userId == null || keyword == null || keyword.isBlank()) {
            return;
        }
        SearchHistory history = new SearchHistory();
        history.setUserId(userId);
        history.setKeyword(keyword.trim());
        searchHistoryMapper.insert(history);
        log.debug("记录搜索历史：用户={}，关键字={}", userId, history.getKeyword());
    }

    @Override
    public List<SearchHistory> myRecent(Long userId) {
        return searchHistoryMapper.selectRecentByUser(userId, 10);
    }
}
