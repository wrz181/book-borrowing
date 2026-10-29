package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.entity.SysUser;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BorrowRecordMapper;
import com.example.bookborrowing.mapper.SysUserMapper;
import com.example.bookborrowing.service.BorrowRecordService;
import com.example.bookborrowing.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 借阅记录业务实现：阅览申请 → 管理员确认 → 借出 → 归还申请 → 管理员确认归还。
 * <p>
 * 数据范围规则：管理员（admin）可读写全部记录；
 * 学生只能读写 borrower_id = 当前登录用户ID 的记录，
 * 范围参数一律由服务端按登录态推导，客户端无法指定。
 */
@Service
public class BorrowRecordServiceImpl implements BorrowRecordService {

    private static final Logger log = LoggerFactory.getLogger(BorrowRecordServiceImpl.class);

    /** 借阅待确认（学生已提交阅览申请，等待管理员确认） */
    private static final int STATUS_PENDING = 0;

    /** 借阅中 */
    private static final int STATUS_BORROWED = 1;

    /** 已归还 */
    private static final int STATUS_RETURNED = 2;

    /** 已驳回 */
    private static final int STATUS_REJECTED = 3;

    /** 归还待确认 */
    private static final int STATUS_RETURN_PENDING = 4;

    private final BorrowRecordMapper borrowRecordMapper;

    private final BookMapper bookMapper;

    private final SysUserMapper sysUserMapper;

    private final OperationLogService operationLogService;

    public BorrowRecordServiceImpl(BorrowRecordMapper borrowRecordMapper, BookMapper bookMapper,
                                   SysUserMapper sysUserMapper, OperationLogService operationLogService) {
        this.borrowRecordMapper = borrowRecordMapper;
        this.bookMapper = bookMapper;
        this.sysUserMapper = sysUserMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public PageResult<BorrowRecord> page(int pageNum, int pageSize, BorrowRecordQueryDTO query, Long scopeBorrowerId) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        List<BorrowRecord> list = borrowRecordMapper.selectPage(offset, pageSize, query, scopeBorrowerId);
        long total = borrowRecordMapper.selectCount(query, scopeBorrowerId);
        log.info("分页查询借阅记录：第 {} 页，每页 {} 条，共 {} 条，数据范围={}",
                Math.max(pageNum, 1), pageSize, total, scopeBorrowerId == null ? "全部（管理员）" : "本人");
        return new PageResult<>(list, total, Math.max(pageNum, 1), pageSize);
    }

    @Override
    public BorrowRecord getDetail(Long id, Long scopeBorrowerId) {
        BorrowRecord record = borrowRecordMapper.selectById(id, scopeBorrowerId);
        if (record == null) {
            log.warn("借阅记录详情不存在或不在数据范围内：id={}，数据范围={}", id, scopeBorrowerId);
            throw new BusinessException(scopeBorrowerId == null ? "借阅记录不存在" : "借阅记录不存在或无权查看");
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(BorrowRecord record, Long currentUserId, boolean manage) {
        if (record.getBookId() == null) {
            throw new BusinessException("请选择借阅图书");
        }
        Book book = bookMapper.selectById(record.getBookId());
        if (book == null) {
            throw new BusinessException("借阅图书不存在");
        }
        if (book.getStock() == null || book.getStock() <= 0) {
            log.warn("借阅校验未通过：图书库存不足，图书ID={}，书名={}", record.getBookId(), book.getBookName());
            throw new BusinessException("图书库存不足，不能申请阅览");
        }
        // 借阅归属由登录态决定：学生强制写本人ID，杜绝前端伪造 borrower_id
        if (!manage || record.getBorrowerId() == null) {
            record.setBorrowerId(currentUserId);
        }
        fillBorrowerFromUser(record, !manage);
        validateAndPrepare(record, null);
        // 提交申请阶段不占用库存，等管理员确认后才扣减
        record.setStatus(STATUS_PENDING);
        record.setReturnDate(null);
        borrowRecordMapper.insert(record);
        logAction(currentUserId, "提交申请", "申请借阅《" + book.getBookName() + "》");
        log.info("提交阅览申请成功：id={}，图书ID={}，借阅人={}，borrower_id={}",
                record.getId(), record.getBookId(), record.getBorrowerName(), record.getBorrowerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BorrowRecord record) {
        BorrowRecord exist = borrowRecordMapper.selectById(record.getId(), null);
        if (exist == null) {
            log.warn("修改借阅记录失败：记录不存在，id={}", record.getId());
            throw new BusinessException("借阅记录不存在");
        }
        if (exist.getStatus() == null || exist.getStatus() != STATUS_PENDING) {
            throw new BusinessException("只有「借阅待确认」的申请可以修改");
        }
        validateAndPrepare(record, record.getId());
        borrowRecordMapper.updateById(record);
        log.info("修改借阅记录成功：id={}，图书ID={}，借阅人={}", record.getId(), record.getBookId(), record.getBorrowerName());
    }

    /**
     * 新增/修改公共校验：图书存在性、R03 重复借阅规则、未归还记录保护。
     *
     * @param record    借阅记录
     * @param excludeId 修改时排除自身的记录ID，新增时传 null
     */
    private void validateAndPrepare(BorrowRecord record, Long excludeId) {
        if (record.getBookId() == null) {
            throw new BusinessException("请选择借阅图书");
        }
        // 关联对象校验：借阅图书必须存在
        Book book = bookMapper.selectById(record.getBookId());
        if (book == null) {
            throw new BusinessException("借阅图书不存在");
        }
        if (record.getBorrowerNo() == null || record.getBorrowerNo().isBlank()) {
            throw new BusinessException("借阅人学号/借阅证号不能为空");
        }
        if (record.getBorrowerName() == null || record.getBorrowerName().isBlank()) {
            throw new BusinessException("借阅人姓名不能为空");
        }
        if (record.getBorrowDate() == null) {
            record.setBorrowDate(LocalDate.now());
        }
        if (record.getDueDate() == null) {
            record.setDueDate(record.getBorrowDate().plusDays(30));
        }
        if (record.getDueDate().isBefore(record.getBorrowDate())) {
            throw new BusinessException("应还日期不能早于借阅日期");
        }
        // R03 重复业务校验：同一借阅人、同一图书、同一借阅日期不得重复生成借阅记录
        int duplicate = borrowRecordMapper.countByBorrowerBookDate(
                record.getBookId(), record.getBorrowerNo(), record.getBorrowDate(), excludeId);
        if (duplicate > 0) {
            log.warn("借阅校验未通过：同一借阅人当天重复借阅同一图书，图书ID={}，借阅人={}", record.getBookId(), record.getBorrowerNo());
            throw new BusinessException("同一借阅人同一天不能重复借阅同一本图书");
        }
        // 业务保护：同一借阅人对同一图书存在未结束记录时不允许再申请
        int unreturned = borrowRecordMapper.countUnreturned(
                record.getBookId(), record.getBorrowerNo(), excludeId);
        if (unreturned > 0) {
            log.warn("借阅校验未通过：存在未结束记录，图书ID={}，借阅人={}", record.getBookId(), record.getBorrowerNo());
            throw new BusinessException("该借阅人存在该图书的未结束借阅记录，不能重复申请");
        }
    }

    /**
     * 借阅人姓名/证号取自登录账号：学生强制覆盖，避免前端伪造他人信息；
     * 管理员仅在留空时补齐。
     *
     * @param record 借阅记录
     * @param force  是否强制覆盖
     */
    private void fillBorrowerFromUser(BorrowRecord record, boolean force) {
        if (record.getBorrowerId() == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(record.getBorrowerId());
        if (user == null) {
            throw new BusinessException("借阅人账号不存在");
        }
        if (force || record.getBorrowerName() == null || record.getBorrowerName().isBlank()) {
            record.setBorrowerName(user.getNickname() == null || user.getNickname().isBlank()
                    ? user.getUsername() : user.getNickname());
        }
        if (force || record.getBorrowerNo() == null || record.getBorrowerNo().isBlank()) {
            record.setBorrowerNo(user.getUsername());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        BorrowRecord exist = borrowRecordMapper.selectById(id, null);
        if (exist == null) {
            log.warn("删除借阅记录失败：记录不存在，id={}", id);
            throw new BusinessException("借阅记录不存在");
        }
        borrowRecordMapper.deleteById(id);
        // 已占用库存（借阅中/归还待确认）的记录删除时恢复图书库存
        if (exist.getStatus() != null
                && (exist.getStatus() == STATUS_BORROWED || exist.getStatus() == STATUS_RETURN_PENDING)) {
            bookMapper.addStock(exist.getBookId());
            bookMapper.updateStatus(exist.getBookId(), 1);
        }
        log.info("删除借阅记录成功：id={}，图书ID={}", id, exist.getBookId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestReturn(Long id, Long currentUserId, boolean manage) {
        BorrowRecord exist = requireRecord(id);
        // 写操作归属校验：学生只能操作本人记录
        if (!manage && (currentUserId == null || !currentUserId.equals(exist.getBorrowerId()))) {
            log.warn("归还校验未通过：非本人记录，id={}，记录归属={}，当前用户={}",
                    id, exist.getBorrowerId(), currentUserId);
            throw new BusinessException("只能归还本人的借阅记录");
        }
        if (exist.getStatus() == null || exist.getStatus() != STATUS_BORROWED) {
            throw new BusinessException("只有「借阅中」的记录可以申请归还");
        }
        int rows = borrowRecordMapper.updateStatus(id, STATUS_BORROWED, STATUS_RETURN_PENDING);
        if (rows == 0) {
            throw new BusinessException("提交失败，记录状态已变化");
        }
        log.info("提交归还申请成功：id={}，图书ID={}，借阅人={}", id, exist.getBookId(), exist.getBorrowerName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long operatorId) {
        BorrowRecord exist = requireRecord(id);
        if (exist.getStatus() == null || exist.getStatus() != STATUS_PENDING) {
            throw new BusinessException("只有「借阅待确认」的申请可以确认");
        }
        int deducted = bookMapper.deductStock(exist.getBookId());
        if (deducted == 0) {
            throw new BusinessException("图书库存不足，无法确认本次借阅");
        }
        int rows = borrowRecordMapper.updateStatus(id, STATUS_PENDING, STATUS_BORROWED);
        if (rows == 0) {
            throw new BusinessException("确认失败，记录状态已变化");
        }
        Book book = bookMapper.selectById(exist.getBookId());
        // 库存借完后图书状态自动置为不可借（0）
        if (book != null && (book.getStock() == null || book.getStock() <= 0)) {
            bookMapper.updateStatus(book.getId(), 0);
        }
        logAction(operatorId, "确认借阅", "确认借出《" + (book == null ? exist.getBookId() : book.getBookName())
                + "》给 " + exist.getBorrowerName());
        log.info("借阅审批通过：id={}，图书ID={}，操作人={}", id, exist.getBookId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason, Long operatorId) {
        BorrowRecord exist = requireRecord(id);
        if (exist.getStatus() == null || exist.getStatus() != STATUS_PENDING) {
            throw new BusinessException("只有「借阅待确认」的申请可以驳回");
        }
        int rows = borrowRecordMapper.updateStatus(id, STATUS_PENDING, STATUS_REJECTED);
        if (rows == 0) {
            throw new BusinessException("驳回失败，记录状态已变化");
        }
        log.info("借阅申请被驳回：id={}，操作人={}，原因={}", id, operatorId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long id, Long operatorId) {
        BorrowRecord exist = requireRecord(id);
        if (exist.getStatus() == null || exist.getStatus() != STATUS_RETURN_PENDING) {
            throw new BusinessException("只有「归还待确认」的记录可以确认归还");
        }
        int rows = borrowRecordMapper.finishReturn(id, LocalDate.now());
        if (rows == 0) {
            throw new BusinessException("确认失败，记录状态已变化");
        }
        // 归还后恢复库存与可借状态
        bookMapper.addStock(exist.getBookId());
        bookMapper.updateStatus(exist.getBookId(), 1);
        Book book = bookMapper.selectById(exist.getBookId());
        String bookName = book == null ? exist.getBookId() + "号" : book.getBookName();
        logAction(operatorId, "确认归还", exist.getBorrowerName() + " 归还《" + bookName + "》");
        log.info("归还审批通过：id={}，图书ID={}，操作人={}", id, exist.getBookId(), operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectReturn(Long id, String reason, Long operatorId) {
        BorrowRecord exist = requireRecord(id);
        if (exist.getStatus() == null || exist.getStatus() != STATUS_RETURN_PENDING) {
            throw new BusinessException("只有「归还待确认」的记录可以驳回");
        }
        int rows = borrowRecordMapper.updateStatus(id, STATUS_RETURN_PENDING, STATUS_BORROWED);
        if (rows == 0) {
            throw new BusinessException("驳回失败，记录状态已变化");
        }
        log.info("归还申请被驳回：id={}，操作人={}，原因={}", id, operatorId, reason);
    }

    /**
     * 查询记录，不存在则抛业务异常。
     *
     * @param id 记录ID
     * @return 借阅记录
     */
    private BorrowRecord requireRecord(Long id) {
        BorrowRecord record = borrowRecordMapper.selectById(id, null);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        return record;
    }

    /**
     * 记录关键操作日志（operation_log），操作人姓名取自登录账号昵称。
     *
     * @param userId 操作用户ID
     * @param action 操作类型
     * @param detail 操作详情
     */
    private void logAction(Long userId, String action, String detail) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        String userName = user == null ? null
                : (user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname());
        operationLogService.record(userId, userName, action, detail);
    }
}
