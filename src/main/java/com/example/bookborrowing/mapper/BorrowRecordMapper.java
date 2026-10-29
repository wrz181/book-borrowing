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
     * @param offset          偏移量
     * @param limit           每页条数
     * @param query           动态查询条件
     * @param scopeBorrowerId 数据范围：非空时仅查询 borrower_id = 该值的记录（本人）；null 表示不限范围（管理权限）
     * @return 借阅记录列表
     */
    List<BorrowRecord> selectPage(@Param("offset") int offset,
                                  @Param("limit") int limit,
                                  @Param("query") BorrowRecordQueryDTO query,
                                  @Param("scopeBorrowerId") Long scopeBorrowerId);

    /**
     * 统计符合条件的借阅记录总数（与 selectPage 条件一致）。
     *
     * @param query           动态查询条件
     * @param scopeBorrowerId 数据范围：非空时仅统计 borrower_id = 该值的记录（本人）；null 表示不限范围（管理权限）
     * @return 总记录数
     */
    long selectCount(@Param("query") BorrowRecordQueryDTO query,
                     @Param("scopeBorrowerId") Long scopeBorrowerId);

    /**
     * 按ID查询借阅记录详情（关联图书名称、作者、分类名称）。
     *
     * @param id              记录ID
     * @param scopeBorrowerId 数据范围：非空时仅当 borrower_id = 该值才返回；null 表示不限范围（管理权限）
     * @return 借阅记录实体，不存在或不在数据范围内返回 null
     */
    BorrowRecord selectById(@Param("id") Long id, @Param("scopeBorrowerId") Long scopeBorrowerId);

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
     * 状态流转：仅当记录处于 fromStatus 时才更新为 toStatus（并发安全）。
     *
     * @param id         记录ID
     * @param fromStatus 期望的原状态
     * @param toStatus   目标状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus);

    /**
     * 归还审批通过：归还待确认(4) → 已归还(2)，并写入实际归还日期。
     *
     * @param id         记录ID
     * @param returnDate 归还日期
     * @return 影响行数
     */
    int finishReturn(@Param("id") Long id, @Param("returnDate") java.time.LocalDate returnDate);

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

    /**
     * 统计某图书未结束（借阅待确认/借阅中/归还待确认）的借阅记录数（删除图书前校验）。
     *
     * @param bookId 图书ID
     * @return 未结束记录数
     */
    int countUnreturnedByBook(@Param("bookId") Long bookId);
}
