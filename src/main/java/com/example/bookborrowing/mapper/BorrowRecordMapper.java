package com.example.bookborrowing.mapper;

import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.BorrowRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 借阅记录 Mapper：含动态条件查询与图书/分类多表关联查询。
 */
@Mapper
public interface BorrowRecordMapper {

    /**
     * 分页动态条件查询借阅记录（关联图书名称、作者、分类名称）。
     *
     * @param offset 偏移量
     * @param limit  每页条数
     * @param query  动态查询条件
     * @return 借阅记录列表
     */
    List<BorrowRecord> selectPage(@Param("offset") int offset,
                                  @Param("limit") int limit,
                                  @Param("query") BorrowRecordQueryDTO query);

    /**
     * 统计符合条件的借阅记录总数（与 selectPage 条件一致）。
     *
     * @param query 动态查询条件
     * @return 总记录数
     */
    long selectCount(@Param("query") BorrowRecordQueryDTO query);

    /**
     * 按ID查询借阅记录详情（关联图书名称、作者、分类名称）。
     *
     * @param id 记录ID
     * @return 借阅记录实体，不存在返回 null
     */
    BorrowRecord selectById(Long id);

    /**
     * 新增借阅记录。
     *
     * @param record 借阅记录实体
     * @return 影响行数
     */
    int insert(BorrowRecord record);

    /**
     * 更新借阅记录。
     *
     * @param record 借阅记录实体
     * @return 影响行数
     */
    int updateById(BorrowRecord record);

    /**
     * 按ID删除借阅记录。
     *
     * @param id 记录ID
     * @return 影响行数
     */
    int deleteById(Long id);

    /**
     * 归还操作：设置归还日期并更新状态为已归还。
     *
     * @param id         记录ID
     * @param returnDate 归还日期
     * @return 影响行数
     */
    int returnRecord(@Param("id") Long id, @Param("returnDate") java.time.LocalDate returnDate);

    /**
     * R03 重复业务校验：统计同一借阅人、同一图书、同一借阅日期的记录数。
     *
     * @param bookId     图书ID
     * @param borrowerNo 借阅人学号/借阅证号
     * @param borrowDate 借阅日期
     * @param excludeId  排除的记录ID（修改时排除自身，新增时传 null）
     * @return 命中记录数
     */
    int countByBorrowerBookDate(@Param("bookId") Long bookId,
                                @Param("borrowerNo") String borrowerNo,
                                @Param("borrowDate") java.time.LocalDate borrowDate,
                                @Param("excludeId") Long excludeId);

    /**
     * 统计同一借阅人对同一图书的未归还记录数（业务保护）。
     *
     * @param bookId     图书ID
     * @param borrowerNo 借阅人学号/借阅证号
     * @param excludeId  排除的记录ID（修改时排除自身，新增时传 null）
     * @return 未归还记录数
     */
    int countUnreturned(@Param("bookId") Long bookId,
                        @Param("borrowerNo") String borrowerNo,
                        @Param("excludeId") Long excludeId);
}
