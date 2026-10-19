package com.example.bookborrowing.common;

import com.example.bookborrowing.entity.PurchaseFlow;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 采购流程节点守卫：把散落在各业务方法里的「流程当前节点是否允许该动作」判断收敛到一处。
 * 语义统一为「不在允许节点集合内即拒绝」，提示语由调用方给出动作名，节点中文名取自 {@link PurchaseFlow#nodeLabel(String)}。
 */
public final class FlowNodeGuard {

    private FlowNodeGuard() {
    }

    /**
     * 断言流程当前节点在允许集合内。
     *
     * @param flow         流程实例（为 null 表示该采购单还没有流程）
     * @param action       动作名（如「提交申请」「驳回」），用于拼装失败提示
     * @param allowedNodes 允许执行该动作的节点集合
     */
    public static void assertFlowNode(PurchaseFlow flow, String action, String... allowedNodes) {
        if (flow == null) {
            throw new BusinessException("该采购单还没有流程实例，请先发起流程");
        }
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedNodes));
        String node = flow.getCurrentNode();
        if (!allowed.contains(node)) {
            throw new BusinessException(action + "失败：流程当前处于「" + PurchaseFlow.nodeLabel(node) + "」节点");
        }
    }
}
