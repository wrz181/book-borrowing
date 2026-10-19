package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.mapper.MessageItemMapper;
import com.example.bookborrowing.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息业务实现。
 */
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageItemMapper messageItemMapper;

    public MessageServiceImpl(MessageItemMapper messageItemMapper) {
        this.messageItemMapper = messageItemMapper;
    }

    @Override
    public PageResult<MessageItem> page(Long userId, Integer readFlag, int pageNum, int pageSize) {
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (page - 1) * size;
        // userId 为 null 时不限接收人（能力保留，当前调用方均传入登录用户ID）
        List<MessageItem> list = messageItemMapper.selectPage(offset, size, userId, readFlag);
        long total = messageItemMapper.selectCount(userId, readFlag);
        return new PageResult<>(list, total, page, size);
    }

    @Override
    public long countUnread(Long userId) {
        return messageItemMapper.countUnread(userId);
    }

    @Override
    public MessageItem create(MessageItem message) {
        if (message.getReceiverId() == null) {
            throw new BusinessException("消息接收人不能为空");
        }
        if (message.getTitle() == null || message.getTitle().isBlank()) {
            throw new BusinessException("消息标题不能为空");
        }
        message.setReadFlag(0);
        messageItemMapper.insert(message);
        return message;
    }

    @Override
    public void read(Long id, Long userId) {
        MessageItem message = messageItemMapper.selectById(id);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        if (!message.getReceiverId().equals(userId)) {
            throw new BusinessException("无权操作他人的消息");
        }
        messageItemMapper.markRead(id);
    }

    @Override
    public void readAll(Long userId) {
        messageItemMapper.markAllRead(userId);
    }
}
