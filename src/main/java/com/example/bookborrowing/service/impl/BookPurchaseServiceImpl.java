package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.FlowNodeGuard;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.dto.PurchaseQueryDTO;
import com.example.bookborrowing.entity.Book;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.entity.MessageItem;
import com.example.bookborrowing.entity.PurchaseFlow;
import com.example.bookborrowing.entity.PurchaseFlowLog;
import com.example.bookborrowing.mapper.BookCategoryMapper;
import com.example.bookborrowing.mapper.BookMapper;
import com.example.bookborrowing.mapper.BookPurchaseMapper;
import com.example.bookborrowing.mapper.PurchaseFlowLogMapper;
import com.example.bookborrowing.mapper.PurchaseFlowMapper;
import com.example.bookborrowing.service.BookPurchaseService;
import com.example.bookborrowing.service.ErpOutboundService;
import com.example.bookborrowing.service.IntegrationAppService;
import com.example.bookborrowing.service.IntegrationBizService;
import com.example.bookborrowing.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BookPurchaseServiceImpl.class);

    /** 采购单号前缀 */
    private static final String NO_PREFIX = "CG";

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookPurchaseMapper bookPurchaseMapper;

    private final BookMapper bookMapper;

    private final BookCategoryMapper bookCategoryMapper;

    private final MessageService messageService;

    private final PurchaseFlowMapper purchaseFlowMapper;

    private final PurchaseFlowLogMapper purchaseFlowLogMapper;

    private final ErpOutboundService erpOutboundService;

    private final IntegrationAppService integrationAppService;

    private final IntegrationBizService integrationBizService;

    public BookPurchaseServiceImpl(BookPurchaseMapper bookPurchaseMapper,
                                   BookMapper bookMapper,
                                   BookCategoryMapper bookCategoryMapper,
                                   MessageService messageService,
                                   PurchaseFlowMapper purchaseFlowMapper,
                                   PurchaseFlowLogMapper purchaseFlowLogMapper,
                                   ErpOutboundService erpOutboundService,
                                   IntegrationAppService integrationAppService,
                                   IntegrationBizService integrationBizService) {
        this.bookPurchaseMapper = bookPurchaseMapper;
        this.bookMapper = bookMapper;
        this.bookCategoryMapper = bookCategoryMapper;
        this.messageService = messageService;
        this.purchaseFlowMapper = purchaseFlowMapper;
        this.purchaseFlowLogMapper = purchaseFlowLogMapper;
        this.erpOutboundService = erpOutboundService;
        this.integrationAppService = integrationAppService;
        this.integrationBizService = integrationBizService;
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
    @Transactional(rollbackFor = Exception.class)
    public void create(BookPurchase purchase, Long userId, String userName) {
        normalize(purchase);
        validate(purchase);
        purchase.setId(null);
        purchase.setPurchaseNo(generateNo());
        purchase.setStatus(BookPurchase.STATUS_DRAFT);
        purchase.setApplicantId(userId);
        purchase.setApplicantName(userName);
        bookPurchaseMapper.insert(purchase);
        // 采购属于「有流程」业务：建单即建流程实例（草稿节点），不存在"没有流程实例的采购单"
        PurchaseFlow flow = new PurchaseFlow();
        flow.setPurchaseId(purchase.getId());
        flow.setBizType(IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE);
        flow.setCurrentNode(PurchaseFlow.NODE_DRAFT);
        flow.setNodeStatus(PurchaseFlow.NODE_STATUS_RUNNING);
        flow.setApplicantId(userId);
        flow.setApplicantName(userName);
        flow.setStartTime(LocalDateTime.now());
        purchaseFlowMapper.insert(flow);
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
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT && exist.getStatus() != BookPurchase.STATUS_CANCELED) {
            throw new BusinessException("仅草稿或已取消的采购单可以删除");
        }
        // 采购单与流程实例同生共死：删单时物理清理流程与轨迹，避免留下孤儿流程
        PurchaseFlow flow = purchaseFlowMapper.selectByPurchaseId(id);
        if (flow != null) {
            purchaseFlowLogMapper.deleteByFlowId(flow.getId());
            purchaseFlowMapper.deletePhysicallyByPurchaseId(id);
        }
        bookPurchaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long id, String remark) {
        BookPurchase exist = getById(id);
        PurchaseFlow flow = purchaseFlowMapper.selectByPurchaseId(id);
        if (flow == null) {
            throw new BusinessException("该采购单没有流程实例，请走『提交申请 → 审批下发 ERP』流程入库");
        }
        if (!PurchaseFlow.NODE_DONE.equals(flow.getCurrentNode())) {
            throw new BusinessException("流程尚未完成（当前节点：" + PurchaseFlow.nodeLabel(flow.getCurrentNode())
                    + "），请等待 ERP 回执后再入库");
        }
        if (exist.getStatus() == null || exist.getStatus() != BookPurchase.STATUS_SUBMITTED) {
            throw new BusinessException("仅流程已完成且采购单处于已提交状态时可以人工补偿入库，当前状态："
                    + statusLabel(exist.getStatus()));
        }
        applyStockIn(exist, remark, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockInInternal(Long id, String remark) {
        BookPurchase exist = getById(id);
        // 幂等：已入库直接抛业务错误，不做二次加库存
        if (exist.getStatus() != null && exist.getStatus() == BookPurchase.STATUS_STOCKED_IN) {
            throw new BusinessException("采购单已入库，无需重复入库");
        }
        applyStockIn(exist, remark, true);
    }

    /**
     * 入库公共逻辑：匹配已有图书增加库存 / 无匹配新建图书，条件更新保证幂等。
     *
     * @param exist    采购单
     * @param remark   入库备注（可空）
     * @param internal 是否流程引擎自动入库（true 时不重复发送申请人入库消息，由流程引擎统一通知）
     */
    private void applyStockIn(BookPurchase exist, String remark, boolean internal) {
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

        LocalDateTime now = LocalDateTime.now();
        int rows = internal
                ? bookPurchaseMapper.markStockInInternal(exist.getId(), now, bookId, stockInRemark)
                : bookPurchaseMapper.markStockIn(exist.getId(), now, bookId, stockInRemark);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，入库失败");
        }

        // 通知申请人入库完成；流程链路（internal）由流程引擎统一发「流程已完成」通知，避免同一事件两条消息
        if (internal) {
            return;
        }
        MessageItem message = new MessageItem();
        message.setTitle("图书采购入库完成");
        message.setContent("采购单 " + exist.getPurchaseNo() + "（" + exist.getBookName() + "）已入库：" + stockInRemark);
        message.setMsgType("PURCHASE");
        message.setReceiverId(exist.getApplicantId());
        messageService.create(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId, String userName) {
        BookPurchase exist = getById(id);
        if (exist.getStatus() != BookPurchase.STATUS_DRAFT && exist.getStatus() != BookPurchase.STATUS_SUBMITTED) {
            throw new BusinessException("仅草稿或已提交的采购单可以取消");
        }
        PurchaseFlow flow = purchaseFlowMapper.selectByPurchaseId(id);
        // 与致远 OA 一致的硬规则：超级节点（等待 ERP 回执）不可撤销；已完成/已取消不可重复操作
        // 历史数据可能没有流程实例，此时保持「无流程也能取消」的兼容，不调用守卫
        if (flow != null) {
            FlowNodeGuard.assertFlowNode(flow, "取消", PurchaseFlow.NODE_DRAFT, PurchaseFlow.NODE_SUBMIT,
                    PurchaseFlow.NODE_APPROVE, PurchaseFlow.NODE_REJECTED);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = bookPurchaseMapper.markCanceled(id, now);
        if (rows == 0) {
            throw new BusinessException("采购单状态已变更，取消失败");
        }
        // 同步终止流程实例：无流程实例（历史数据）时无需终止
        if (flow == null) {
            return;
        }
        PurchaseFlow update = new PurchaseFlow();
        update.setId(flow.getId());
        update.setCurrentNode(PurchaseFlow.NODE_CANCELED);
        update.setNodeStatus(PurchaseFlow.NODE_STATUS_CANCELED);
        update.setFinishTime(now);
        purchaseFlowMapper.updateById(update);

        // 流程已终止：主动通知 ERP 撤销其侧表单（失败仅告警，不影响本地事务）
        cancelErpFormQuietly(flow, "采购单已被取消");

        PurchaseFlowLog flowLog = new PurchaseFlowLog();
        flowLog.setFlowId(flow.getId());
        flowLog.setFromNode(flow.getCurrentNode());
        flowLog.setToNode(PurchaseFlow.NODE_CANCELED);
        flowLog.setAction(PurchaseFlowLog.ACTION_CANCEL);
        flowLog.setOperatorId(userId);
        flowLog.setOperatorName(userName == null ? "系统" : userName);
        flowLog.setComment("采购单已取消，流程同步终止");
        purchaseFlowLogMapper.insert(flowLog);
    }

    /**
     * 通知 ERP 撤销其侧表单（OA 侧流程已终止）。取不到集成应用/集成业务时只告警跳过；
     * 出站方法自身不抛异常，不影响本地事务。
     *
     * @param flow   流程实例
     * @param reason 撤销说明
     */
    private void cancelErpFormQuietly(PurchaseFlow flow, String reason) {
        if (flow == null || flow.getErpFormId() == null) {
            return;
        }
        IntegrationApp app = flow.getAppId() == null ? null : integrationAppService.getById(flow.getAppId());
        String bizType = flow.getBizType() == null ? IntegrationBiz.BIZ_TYPE_BOOK_PURCHASE : flow.getBizType();
        IntegrationBiz biz = integrationBizService.getByBizType(bizType);
        if (app == null || biz == null) {
            log.warn("通知 ERP 撤销表单跳过：purchaseId={}，erpFormId={}，appId={}，bizType={}（集成应用或集成业务配置缺失）",
                    flow.getPurchaseId(), flow.getErpFormId(), flow.getAppId(), bizType);
            return;
        }
        erpOutboundService.cancelErpForm(app, biz, flow.getErpFormId(), reason);
    }

    /**
     * 判断流程是否已结束（完成 / 已打回 / 已取消）。
     *
     * @param node 当前节点
     * @return 已结束返回 true
     */
    private boolean isFlowFinished(String node) {
        return PurchaseFlow.NODE_DONE.equals(node)
                || PurchaseFlow.NODE_REJECTED.equals(node)
                || PurchaseFlow.NODE_CANCELED.equals(node);
    }

    /**
     * 采购单状态中文名，用于错误提示。
     *
     * @param status 状态值
     * @return 中文名称，未知状态原样返回
     */
    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case BookPurchase.STATUS_DRAFT -> "草稿";
            case BookPurchase.STATUS_SUBMITTED -> "已提交";
            case BookPurchase.STATUS_STOCKED_IN -> "已入库";
            case BookPurchase.STATUS_ACKED -> "已回执";
            case BookPurchase.STATUS_CANCELED -> "已取消";
            default -> String.valueOf(status);
        };
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
}
