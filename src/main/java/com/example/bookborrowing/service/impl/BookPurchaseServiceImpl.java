package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.mapper.BookCategoryMapper;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BookPurchaseMapper;
import com.example.bookborrowing.mapper.IntegrationFormMapper;
import com.example.bookborrowing.service.BookPurchaseService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图书采购业务实现。
 */
@Service
public class BookPurchaseServiceImpl implements BookPurchaseService {

    /** 采购单号前缀 */
    private static final String NO_PREFIX = "CG";

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookPurchaseMapper bookPurchaseMapper;

    private final BookMapper bookMapper;

    private final BookCategoryMapper bookCategoryMapper;

    private final IntegrationFormMapper integrationFormMapper;

    private final IntegrationAppService integrationAppService;

    private final MessageService messageService;

    public BookPurchaseServiceImpl(BookPurchaseMapper bookPurchaseMapper,
                                   BookMapper bookMapper,
                                   BookCategoryMapper bookCategoryMapper,
                                   IntegrationFormMapper integrationFormMapper,
                                   IntegrationAppService integrationAppService,
                                   MessageService messageService) {
        this.bookPurchaseMapper = bookPurchaseMapper;
        this.bookMapper = bookMapper;
        this.bookCategoryMapper = bookCategoryMapper;
        this.integrationFormMapper = integrationFormMapper;
        this.integrationAppService = integrationAppService;
        this.messageService = messageService;
    }

    @Override
    public PageResult<BookPurchase> page(int pageNum, int pageSize, PurchaseQueryDTO query) {
        int page = Math.max(pageNum, 1);
        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (page - 1) * size;
        List<BookPurchase> list = bookPurchaseMapper.selectPage(offset, size, query);
        long total = bookPurchaseMapper.selectCount(query);
        return new PageResult<>(list, total, page, size);
    }

    @Override
    public BookPurchase getById(Long id) {
        BookPurchase purchase = bookPurchaseMapper.selectById(id);
        if (purchase == null) {
            throw new BusinessException("采购单不存在");
        }
        return purchase;
    }

    @Override
    public void create(BookPurchase purchase, Long userId, String userName) {
        normalize(purchase);
        validate(purchase);
        purchase.setId(null);
        purchase.setPurchaseNo(generateNo());
        purchase.setStatus(BookPurchase.STATUS_DRAFT);
        purchase.setApplicantId(userId);
        purchase.setApplicantName(userName);
        bookPurchaseMapper.insert(purchase);
    }

    @Override
    public void update(BookPurchase purchase) {
        BookPurchase exist = getById(purchase.getId());
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT) {
            throw new BusinessException("仅草稿状态的采购单可以修改");
        }
        normalize(purchase);
        validate(purchase);
        int rows = bookPurchaseMapper.updateById(purchase);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，修改失败");
        }
    }

    @Override
    public void delete(Long id) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT && exist.getStatus() != BookPurchase.STATUS_CANCELED) {
            throw new BusinessException("仅草稿或已取消的采购单可以删除");
        }
        bookPurchaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long appId, String userName) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT) {
            throw new BusinessException("仅草稿状态的采购单可以提交");
        }

        Long formId = null;
        if (appId != null) {
            // 下发集成表单给外部平台（状态 0-待处理），等待对方回执
            IntegrationApp app = integrationAppService.getById(appId);
            if (app == null) {
                throw new BusinessException("所选集成应用不存在");
            }
            if (app.getEnabled() == null || app.getEnabled() != 1) {
                throw new BusinessException("所选集成应用已停用");
            }
            IntegrationForm form = new IntegrationForm();
            form.setAppId(appId);
            form.setTitle("图书采购单 " + exist.getPurchaseNo() + "：" + exist.getBookName()
                    + " ×" + exist.getQuantity() + " 册");
            form.setFormType("BOOK_PURCHASE");
            form.setContent(buildFormContent(exist));
            form.setStatus(0);
            form.setSenderId(exist.getApplicantId());
            form.setSenderName(userName == null ? exist.getApplicantName() : userName);
            integrationFormMapper.insert(form);
            formId = form.getId();
        }

        int rows = bookPurchaseMapper.markSubmitted(id, appId, formId, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，提交失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long id, String remark) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_SUBMITTED) {
            throw new BusinessException("仅已提交的采购单可以入库");
        }
        // 已下发外部平台的，须等待对方回执（集成表单已处理）后方可入库
        if (exist.getIntegrationFormId() != null) {
            IntegrationForm form = integrationFormMapper.selectById(exist.getIntegrationFormId());
            if (form != null && (form.getStatus() == null || form.getStatus() != 1)) {
                throw new BusinessException("外部平台尚未回执，请等待处理完成后再入库");
            }
        }

        int quantity = exist.getQuantity() == null || exist.getQuantity() < 1 ? 1 : exist.getQuantity();
        Long bookId;
        String stockInRemark;
        Book matched = bookMapper.selectForStockIn(exist.getIsbn(), exist.getBookName());
        if (matched != null) {
            bookMapper.addStockBy(matched.getId(), quantity);
            bookId = matched.getId();
            stockInRemark = "匹配已有图书【" + matched.getBookName() + "】，库存 +" + quantity + " 册";
        } else {
            Book book = new Book();
            book.setBookName(exist.getBookName());
            book.setAuthor(exist.getAuthor());
            book.setIsbn(exist.getIsbn());
            book.setPublisher(exist.getPublisher());
            book.setCategoryId(exist.getCategoryId());
            book.setPrice(exist.getUnitPrice() == null ? BigDecimal.ZERO : exist.getUnitPrice());
            book.setStock(quantity);
            book.setStatus(1);
            bookMapper.insert(book);
            bookId = book.getId();
            stockInRemark = "未匹配到已有图书，新增图书并入库 " + quantity + " 册";
        }
        if (remark != null && !remark.isBlank()) {
            stockInRemark = stockInRemark + "；备注：" + remark;
        }

        int rows = bookPurchaseMapper.markStockIn(id, LocalDateTime.now(), bookId, stockInRemark);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，入库失败");
        }

        // 通知申请人入库完成
        MessageItem message = new MessageItem();
        message.setTitle("图书采购入库完成");
        message.setContent("采购单 " + exist.getPurchaseNo() + "（" + exist.getBookName() + "）已入库：" + stockInRemark);
        message.setMsgType("PURCHASE");
        message.setReceiverId(exist.getApplicantId());
        messageService.create(message);
    }

    @Override
    public void cancel(Long id) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT && exist.getStatus() != BookPurchase.STATUS_SUBMITTED) {
            throw new BusinessException("仅草稿或已提交的采购单可以取消");
        }
        int rows = bookPurchaseMapper.markCanceled(id, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，取消失败");
        }
    }

    /**
     * 归一化字段：总价 = 数量 × 单价。
     *
     * @param purchase 采购单实体
     */
    private void normalize(BookPurchase purchase) {
        purchase.setPurchaseNo(null);
        purchase.setId(purchase.getId());
        if (purchase.getQuantity() == null || purchase.getQuantity() < 1) {
            purchase.setQuantity(1);
        }
        BigDecimal price = purchase.getUnitPrice() == null ? BigDecimal.ZERO : purchase.getUnitPrice();
        purchase.setUnitPrice(price);
        purchase.setTotalPrice(price.multiply(BigDecimal.valueOf(purchase.getQuantity())));
    }

    /**
     * 校验采购单关键字段。
     *
     * @param purchase 采购单实体
     */
    private void validate(BookPurchase purchase) {
        if (purchase.getBookName() == null || purchase.getBookName().isBlank()) {
            throw new BusinessException("图书名称不能为空");
        }
        if (purchase.getCategoryId() == null) {
            throw new BusinessException("请选择所属图书分类");
        }
        if (bookCategoryMapper.selectById(purchase.getCategoryId()) == null) {
            throw new BusinessException("所属图书分类不存在");
        }
        if (purchase.getUnitPrice() != null && purchase.getUnitPrice().signum() < 0) {
            throw new BusinessException("单价不能为负数");
        }
    }

    /**
     * 生成采购单号：CG + yyyyMMddHHmmss + 3 位随机数。
     *
     * @return 采购单号
     */
    private String generateNo() {
        return NO_PREFIX + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 构造下发给外部平台的表单内容（JSON）。
     *
     * @param purchase 采购单
     * @return JSON 字符串
     */
    private String buildFormContent(BookPurchase purchase) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"purchaseId\":").append(purchase.getId()).append(',');
        sb.append("\"purchaseNo\":\"").append(escape(purchase.getPurchaseNo())).append("\",");
        sb.append("\"bookName\":\"").append(escape(purchase.getBookName())).append("\",");
        sb.append("\"author\":\"").append(escape(purchase.getAuthor())).append("\",");
        sb.append("\"isbn\":\"").append(escape(purchase.getIsbn())).append("\",");
        sb.append("\"publisher\":\"").append(escape(purchase.getPublisher())).append("\",");
        sb.append("\"quantity\":").append(purchase.getQuantity()).append(',');
        sb.append("\"unitPrice\":").append(purchase.getUnitPrice()).append(',');
        sb.append("\"totalPrice\":").append(purchase.getTotalPrice()).append(',');
        sb.append("\"source\":\"图书借阅管理系统\"");
        sb.append('}');
        return sb.toString();
    }

    /**
     * 简单 JSON 字符串转义。
     *
     * @param value 原值
     * @return 转义后的值（null 视为空串）
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
