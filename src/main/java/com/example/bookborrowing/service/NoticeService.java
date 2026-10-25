package com.example.bookborrowing.service;

import com.example.bookborrowing.entity.SysNotice;

import java.util.List;

/**
 * 系统公告业务接口。
 */
public interface NoticeService {

    /**
     * 查询全部公告（时间倒序）。
     *
     * @return 公告列表
     */
    List<SysNotice> listAll();

    /**
     * 查询最新一条公告。
     *
     * @return 公告，无公告返回 null
     */
    SysNotice latest();

    /**
     * 发布公告（仅管理员）。
     *
     * @param notice        公告（title/content）
     * @param publisherName 发布人姓名
     */
    void add(SysNotice notice, String publisherName);

    /**
     * 删除公告（仅管理员）。
     *
     * @param id 公告ID
     */
    void remove(Long id);
}
