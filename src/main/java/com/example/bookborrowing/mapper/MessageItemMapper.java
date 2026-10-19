package com.example.bookborrowing.mapper;

import com.example.bookborrowing.entity.MessageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息 Mapper。
 */
@Mapper
public interface MessageItemMapper {

    /**
     * 分页查询消息。
     *
     * @param offset     偏移量
     * @param limit      每页条数
     * @param receiverId 接收人ID（null 表示不限接收人，能力保留）
     * @param readFlag   是否已读（可空，null 表示全部）
     * @return 消息列表
     */
    List<MessageItem> selectPage(@Param("offset") int offset,
                                 @Param("limit") int limit,
                                 @Param("receiverId") Long receiverId,
                                 @Param("readFlag") Integer readFlag);

    /**
     * 统计消息总数。
     *
     * @param receiverId 接收人ID（null 表示不限接收人，能力保留）
     * @param readFlag   是否已读（可空）
     * @return 总记录数
     */
    long selectCount(@Param("receiverId") Long receiverId, @Param("readFlag") Integer readFlag);

    /**
     * 统计某接收人的未读消息数。
     *
     * @param receiverId 接收人ID
     * @return 未读数量
     */
    long countUnread(Long receiverId);

    /**
     * 按ID查询消息。
     *
     * @param id 消息ID
     * @return 消息实体，不存在返回 null
     */
    MessageItem selectById(Long id);

    /**
     * 新增消息。
     *
     * @param message 消息实体
     * @return 影响行数
     */
    int insert(MessageItem message);

    /**
     * 标记单条消息已读。
     *
     * @param id 消息ID
     * @return 影响行数
     */
    int markRead(Long id);

    /**
     * 标记某接收人全部消息已读。
     *
     * @param receiverId 接收人ID
     * @return 影响行数
     */
    int markAllRead(Long receiverId);
}
