package com.example.bookborrowing.service;

import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.MessageItem;

/**
 * 消息业务接口。
 */
public interface MessageService {

    /**
     * 分页查询某用户的消息。
     *
     * @param userId   接收人ID
     * @param readFlag 是否已读（可空）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<MessageItem> page(Long userId, Integer readFlag, int pageNum, int pageSize);

    /**
     * 统计某用户未读消息数。
     *
     * @param userId 接收人ID
     * @return 未读数量
     */
    long countUnread(Long userId);

    /**
     * 新增消息（集成回调调用）。
     *
     * @param message 消息实体
     * @return 保存后的实体（含ID）
     */
    MessageItem create(MessageItem message);

    /**
     * 标记单条消息已读。
     *
     * @param id     消息ID
     * @param userId 当前用户ID（校验归属）
     */
    void read(Long id, Long userId);

    /**
     * 标记某用户全部消息已读。
     *
     * @param userId 接收人ID
     */
    void readAll(Long userId);
}
