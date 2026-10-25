package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BookComment;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.BookCommentMapper;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图书评论业务实现。
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final BookCommentMapper bookCommentMapper;

    private final BookMapper bookMapper;

    private final SysUserMapper sysUserMapper;

    public CommentServiceImpl(BookCommentMapper bookCommentMapper, BookMapper bookMapper,
                              SysUserMapper sysUserMapper) {
        this.bookCommentMapper = bookCommentMapper;
        this.bookMapper = bookMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<BookComment> listByBook(Long bookId) {
        return bookCommentMapper.selectByBookId(bookId);
    }

    @Override
    public void add(BookComment comment, Long currentUserId) {
        // 关联对象校验：被评论的图书必须存在
        if (comment.getBookId() == null || bookMapper.selectById(comment.getBookId()) == null) {
            throw new BusinessException("图书不存在，无法评论");
        }
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (comment.getRating() == null || comment.getRating() < 1 || comment.getRating() > 5) {
            comment.setRating(5);
        }
        // 评论人与用户ID由服务端按登录态确定，不信任前端
        SysUser user = sysUserMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException("登录状态无效");
        }
        comment.setUserId(user.getId());
        comment.setUserName(user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname());
        bookCommentMapper.insert(comment);
        log.info("发表图书评论成功：id={}，图书ID={}，用户={}", comment.getId(), comment.getBookId(), comment.getUserName());
    }

    @Override
    public void remove(Long id, Long currentUserId, boolean isAdmin) {
        BookComment exist = bookCommentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("评论不存在");
        }
        // 只有评论本人或管理员可以删除
        if (!isAdmin && !currentUserId.equals(exist.getUserId())) {
            throw new BusinessException("只能删除本人的评论");
        }
        bookCommentMapper.deleteById(id);
        log.info("删除图书评论成功：id={}，操作人={}", id, currentUserId);
    }
}
