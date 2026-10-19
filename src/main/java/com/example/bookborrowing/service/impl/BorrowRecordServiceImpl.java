package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.common.PermissionCache;
import com.example.bookborrowing.dto.BorrowRecordQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BorrowRecord;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BorrowRecordMapper;
import com.example.bookborrowing.mapper.SysPermissionMapper;
import com.example.bookborrowing.mapper.SysUserRoleMapper;
import com.example.bookborrowing.service.BorrowRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 借阅记录业务实现：R03 重复业务校验与库存联动，并按登录身份收敛数据范围。
 * <p>
 * 数据范围规则：持有 borrow:manage（集团管理员）可读全部记录；其余账号（普通人员、系统管理员等）
 * 只能读写 borrower_id = 当前登录用户ID 的记录，范围参数一律由服务端按登录态推导，客户端无法指定。
 */
@Service
public class BorrowRecordServiceImpl implements BorrowRecordService {

    private static final Logger log = LoggerFactory.getLogger(BorrowRecordServiceImpl.class);

    /** 借阅管理权限编码：持有该权限才可跨用户查看/维护借阅记录 */
    private static final String PERM_BORROW_MANAGE = "borrow:manage";

    private final BorrowRecordMapper borrowRecordMapper;

    private final BookMapper bookMapper;

    private final SysPermissionMapper sysPermissionMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final PermissionCache permissionCache;

    public BorrowRecordServiceImpl(BorrowRecordMapper borrowRecordMapper, BookMapper bookMapper,
                                   SysPermissionMapper sysPermissionMapper, SysUserRoleMapper sysUserRoleMapper,
                                   PermissionCache permissionCache) {
        this.borrowRecordMapper = borrowRecordMapper;
        this.bookMapper = bookMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.permissionCache = permissionCache;
    }

    @Override
    public PageResult<BorrowRecord> page(int pageNum, int pageSize, BorrowRecordQueryDTO query, Long scopeBorrowerId) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        List<BorrowRecord> list = borrowRecordMapper.selectPage(offset, pageSize, query, scopeBorrowerId);
        long total = borrowRecordMapper.selectCount(query, scopeBorrowerId);
        log.info("分页查询借阅记录：第 {} 页，每页 {} 条，共 {} 条，数据范围={}",
                Math.max(pageNum, 1), pageSize, total, scopeBorrowerId == null ? "全部（borrow:manage）" : "本人");
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
        validateAndPrepare(record, null);
        // 新增记录默认借阅中
        record.setStatus(1);
        record.setReturnDate(null);
        // 借阅归属由登录态决定：非管理身份强制写本人ID，管理身份未指定时也落本人，杜绝前端伪造 borrower_id
        if (!manage || record.getBorrowerId() == null) {
            record.setBorrowerId(currentUserId);
        }
        borrowRecordMapper.insert(record);
        // 扣减图书库存
        bookMapper.deductStock(record.getBookId());
        log.info("新增借阅记录成功：id={}，图书ID={}，借阅人={}，borrower_id={}",
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
        // R02 关联对象校验：借阅图书必须存在
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
            throw new BusinessException("应还日期不能为空");
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
        // 业务保护：同一借阅人对同一图书存在未归还记录时不允许再借
        int unreturned = borrowRecordMapper.countUnreturned(
                record.getBookId(), record.getBorrowerNo(), excludeId);
        if (unreturned > 0) {
            log.warn("借阅校验未通过：存在未归还记录，图书ID={}，借阅人={}", record.getBookId(), record.getBorrowerNo());
            throw new BusinessException("该借阅人存在该图书的未归还记录，不能重复借阅");
        }
        // 库存校验（修改记录时借阅日期不变，库存已在借出时扣减，这里仅对新增生效）
        if (excludeId == null && (book.getStock() == null || book.getStock() <= 0)) {
            log.warn("借阅校验未通过：图书库存不足，图书ID={}，书名={}", record.getBookId(), book.getBookName());
            throw new BusinessException("图书库存不足，不能借阅");
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
        // 删除未归还记录时恢复图书库存
        if (exist.getStatus() != null && exist.getStatus() == 1) {
            bookMapper.addStock(exist.getBookId());
        }
        log.info("删除借阅记录成功：id={}，图书ID={}", id, exist.getBookId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(Long id, Long currentUserId, boolean manage) {
        BorrowRecord exist = borrowRecordMapper.selectById(id, null);
        if (exist == null) {
            log.warn("归还失败：借阅记录不存在，id={}", id);
            throw new BusinessException("借阅记录不存在");
        }
        // 写操作归属校验：非借阅管理身份只能归还本人记录（历史 borrower_id 为空的记录仅管理身份可归还）
        if (!manage && (currentUserId == null || !currentUserId.equals(exist.getBorrowerId()))) {
            log.warn("归还校验未通过：非本人记录，id={}，记录归属={}，当前用户={}",
                    id, exist.getBorrowerId(), currentUserId);
            throw new BusinessException("只能归还本人的借阅记录");
        }
        if (exist.getStatus() == null || exist.getStatus() != 1) {
            log.warn("归还失败：该记录已归还，id={}", id);
            throw new BusinessException("该记录已归还，不能重复归还");
        }
        int rows = borrowRecordMapper.returnRecord(id, LocalDate.now());
        if (rows == 0) {
            log.warn("归还失败：记录状态已变化，id={}", id);
            throw new BusinessException("归还失败，记录状态已变化");
        }
        // 归还后恢复图书库存
        bookMapper.addStock(exist.getBookId());
        log.info("归还图书成功：id={}，图书ID={}，借阅人={}", id, exist.getBookId(), exist.getBorrowerName());
    }

    @Override
    public boolean hasBorrowManage(Long userId, Long activeRoleId) {
        if (userId == null) {
            return false;
        }
        // 身份角色须属于该用户，非法身份按全身份并集处理（与权限拦截器一致）
        Long roleId = activeRoleId;
        if (roleId != null && sysUserRoleMapper.existsUserRole(userId, roleId) == 0) {
            roleId = null;
        }
        String cacheKey = roleId == null ? "u:" + userId : "u:" + userId + ":r:" + roleId;
        Set<String> perms = permissionCache.get(cacheKey);
        if (perms == null) {
            List<String> codes = roleId == null
                    ? sysPermissionMapper.selectPermCodesByUserId(userId)
                    : sysPermissionMapper.selectPermCodesByUserIdAndRole(userId, roleId);
            perms = new HashSet<>(codes);
            permissionCache.put(cacheKey, perms);
        }
        return perms.contains(PERM_BORROW_MANAGE);
    }
}
