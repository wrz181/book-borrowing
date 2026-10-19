package com.example.bookborrowing.job;

import com.example.bookborrowing.service.PurchaseFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超级节点（ERP）回调超时巡检任务。
 * 每 60 秒扫描一次挂起在超级节点（current_node = SUPER 且 node_status = 0）的采购流程，
 * 对超过 integration_biz.callback_timeout_minutes（未配置按 1440 分钟）仍未收到 ERP 回执的流程自动打回。
 * 每条流程由 {@link PurchaseFlowService#timeoutIfExpired(Long)} 在独立事务内处理，一条失败不影响其它条。
 */
@Component
public class PurchaseFlowTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(PurchaseFlowTimeoutJob.class);

    private final PurchaseFlowService purchaseFlowService;

    public PurchaseFlowTimeoutJob(PurchaseFlowService purchaseFlowService) {
        this.purchaseFlowService = purchaseFlowService;
    }

    /**
     * 巡检入口：查出等待 ERP 回执的流程，逐条判定是否超时并打回。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void scanTimeoutFlows() {
        List<Long> purchaseIds;
        try {
            purchaseIds = purchaseFlowService.listSuperNodeWaitPurchaseIds();
        } catch (Exception e) {
            log.warn("超级节点回调超时巡检：查询待回执流程失败，本轮跳过", e);
            return;
        }
        if (purchaseIds == null || purchaseIds.isEmpty()) {
            return;
        }
        int handled = 0;
        int failed = 0;
        for (Long purchaseId : purchaseIds) {
            try {
                if (purchaseFlowService.timeoutIfExpired(purchaseId)) {
                    handled++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("超级节点回调超时巡检：采购单 {} 超时打回失败，已跳过（不影响其它流程）", purchaseId, e);
            }
        }
        log.info("超级节点回调超时巡检：待回执 {} 条，本轮超时打回 {} 条，失败 {} 条",
                purchaseIds.size(), handled, failed);
    }
}
