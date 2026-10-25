package com.example.bookborrowing.controller;

import com.example.bookborrowing.common.Result;
import com.example.bookborrowing.entity.SysNotice;
import com.example.bookborrowing.service.NoticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统公告接口：全体用户可查看，管理员可发布与删除。
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /**
     * 查询全部公告（时间倒序）。
     *
     * @return 公告列表
     */
    @GetMapping
    public Result<List<SysNotice>> list() {
        return Result.success(noticeService.listAll());
    }

    /**
     * 查询最新一条公告。
     *
     * @return 公告
     */
    @GetMapping("/latest")
    public Result<SysNotice> latest() {
        return Result.success(noticeService.latest());
    }

    /**
     * 发布公告（仅管理员）。
     *
     * @param notice  公告（title/content）
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping
    public Result<Void> add(@RequestBody SysNotice notice, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("currentRole"))) {
            return Result.error(403, "仅管理员可发布公告");
        }
        noticeService.add(notice, String.valueOf(request.getAttribute("currentUserName")));
        return Result.success();
    }

    /**
     * 删除公告（仅管理员）。
     *
     * @param id      公告ID
     * @param request HTTP 请求
     * @return 空响应
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("currentRole"))) {
            return Result.error(403, "仅管理员可删除公告");
        }
        noticeService.remove(id);
        return Result.success();
    }
}
