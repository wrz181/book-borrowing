package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.BookComment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 图书评论 Mapper。
 */
@Mapper
public interface BookCommentMapper {

    /** 新增评论。 */
    int insert(BookComment comment);

    /** 按图书查询评论（时间倒序）。 */
    List<BookComment> selectByBookId(Long bookId);

    /** 按ID查询评论。 */
    BookComment selectById(Long id);

    /** 按ID逻辑删除评论。 */
    int deleteById(Long id);
}
