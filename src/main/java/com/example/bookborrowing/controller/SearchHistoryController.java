package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.SearchHistory;
import com.example.bookborrowing.service.SearchHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索历史接口：搜索记录由图书动态查询自动产生，用户可查看本人最近搜索。
 */
@RestController
@RequestMapping("/api/search-histories")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    public SearchHistoryController(SearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    /**
     * 查询本人最近 10 条搜索记录（时间倒序）。
     *
     * @param request HTTP 请求
     * @return 搜索记录列表
     */
    @GetMapping("/my")
    public Result<List<SearchHistory>> my(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(searchHistoryService.myRecent(userId));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUserId");
        return attr instanceof Long userId ? userId : null;
    }
}
