package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.SysNotice;
import com.example.bookborrowing.mapper.SysNoticeMapper;
import com.example.bookborrowing.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统公告业务实现。
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeServiceImpl.class);

    private final SysNoticeMapper sysNoticeMapper;

    public NoticeServiceImpl(SysNoticeMapper sysNoticeMapper) {
        this.sysNoticeMapper = sysNoticeMapper;
    }

    @Override
    public List<SysNotice> listAll() {
        return sysNoticeMapper.selectAll();
    }

    @Override
    public SysNotice latest() {
        return sysNoticeMapper.selectLatest();
    }

    @Override
    public void add(SysNotice notice, String publisherName) {
        if (notice.getTitle() == null || notice.getTitle().isBlank()) {
            throw new BusinessException("公告标题不能为空");
        }
        if (notice.getContent() == null || notice.getContent().isBlank()) {
            throw new BusinessException("公告内容不能为空");
        }
        notice.setPublisherName(publisherName);
        sysNoticeMapper.insert(notice);
        log.info("发布公告成功：id={}，标题={}", notice.getId(), notice.getTitle());
    }

    @Override
    public void remove(Long id) {
        SysNotice exist = sysNoticeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("公告不存在");
        }
        sysNoticeMapper.deleteById(id);
        log.info("删除公告成功：id={}，标题={}", id, exist.getTitle());
    }
}
