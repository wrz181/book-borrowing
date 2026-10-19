package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.entity.BookPurchase;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.entity.IntegrationForm;
import com.example.bookborrowing.service.ErpOutboundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出站调用类ERP平台实现：JDK 自带 HttpClient（不引入新依赖），失败一律抛业务异常，由调用方事务回滚。
 */
@Service
public class ErpOutboundServiceImpl implements ErpOutboundService {

    /** 出站模块日志 */
    private static final Logger log = LoggerFactory.getLogger(ErpOutboundServiceImpl.class);

    /** 连接超时：3 秒 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** 读超时：10 秒 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** 业务类型固定为图书采购 */
    private static final String BIZ_TYPE_BOOK_PURCHASE = "BOOK_PURCHASE";

    /** 出站接口路径（未配置 superNodeUrl 时拼在应用地址后） */
    private static final String FORM_PATH = "/erp/forms";

    /** ERP 协议成功码 */
    private static final int ERP_CODE_SUCCESS = 200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Long pushFormToErp(IntegrationBiz biz, IntegrationForm form, BookPurchase purchase, IntegrationApp app) {
        if (biz == null) {
            throw new BusinessException("未配置图书采购的集成业务/超级节点，请先在后台配置");
        }
        if (form == null || form.getId() == null) {
            throw new BusinessException("集成表单尚未保存，无法下发 ERP");
        }
        if (purchase == null || purchase.getId() == null) {
            throw new BusinessException("采购单不存在，无法下发 ERP");
        }
        String url = resolveUrl(biz, app);
        String body = buildBody(biz, form, purchase);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("X-App-Id", String.valueOf(app.getId()))
                .header("X-App-Secret", app.getSsoSecret() == null ? "" : app.getSsoSecret())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        log.info("下发 ERP 创建表单，url={}, formId={}, purchaseNo={}", url, form.getId(), purchase.getPurchaseNo());
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下发 ERP 被中断，url={}, formId={}", url, form.getId());
            throw new BusinessException("调用 ERP 创建表单被中断，请稍后重试");
        } catch (Exception e) {
            log.error("下发 ERP 失败，url={}, formId={}, 原因={}", url, form.getId(), e.toString());
            throw new BusinessException("调用 ERP 创建表单失败：" + summarize(e));
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.error("下发 ERP 返回非 2xx，url={}, formId={}, HTTP={}, body={}",
                    url, form.getId(), status, abbreviate(response.body()));
            throw new BusinessException("调用 ERP 创建表单失败：HTTP " + status);
        }
        Long erpFormId = parseErpFormId(response.body(), status);
        log.info("下发 ERP 成功，formId={}, purchaseNo={}, erpFormId={}",
                form.getId(), purchase.getPurchaseNo(), erpFormId);
        return erpFormId;
    }

    @Override
    public void cancelErpForm(IntegrationApp app, IntegrationBiz biz, Long erpFormId, String reason) {
        if (erpFormId == null) {
            log.warn("通知 ERP 撤销表单跳过：erpFormId 为空");
            return;
        }
        String url;
        try {
            url = resolveCancelUrl(biz, app, erpFormId);
        } catch (Exception e) {
            log.warn("通知 ERP 撤销表单失败（出站地址解析）：erpFormId={}，原因={}", erpFormId, summarize(e));
            return;
        }
        String body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>(2);
            payload.put("reason", nullToEmpty(reason));
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("通知 ERP 撤销表单失败（报文构造）：erpFormId={}，原因={}", erpFormId, summarize(e));
            return;
        }

        log.info("通知 ERP 撤销表单，url={}, erpFormId={}, reason={}", url, erpFormId, reason);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("X-App-Id", app == null || app.getId() == null ? "" : String.valueOf(app.getId()))
                    .header("X-App-Secret", app == null || app.getSsoSecret() == null ? "" : app.getSsoSecret())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("通知 ERP 撤销表单返回非 2xx，url={}, erpFormId={}, HTTP={}, body={}",
                        url, erpFormId, status, abbreviate(response.body()));
                return;
            }
            log.info("通知 ERP 撤销表单成功，erpFormId={}, url={}", erpFormId, url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("通知 ERP 撤销表单被中断，url={}, erpFormId={}", url, erpFormId);
        } catch (Exception e) {
            log.warn("通知 ERP 撤销表单失败，url={}, erpFormId={}, 原因={}", url, erpFormId, e.toString());
        }
    }

    /**
     * 解析撤单地址：与 {@link #resolveUrl} 保持同一 base，仅把 /erp/forms 换成 /erp/forms/{erpFormId}/cancel。
     *
     * @param biz       集成业务配置
     * @param app       集成应用
     * @param erpFormId ERP 表单号
     * @return 撤单地址
     */
    private String resolveCancelUrl(IntegrationBiz biz, IntegrationApp app, Long erpFormId) {
        String base = resolveUrl(biz, app);
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(FORM_PATH)) {
            base = base.substring(0, base.length() - FORM_PATH.length());
        }
        return base + FORM_PATH + "/" + erpFormId + "/cancel";
    }

    /**
     * 解析出站地址：superNodeUrl 优先，否则 appUrl 去掉末尾 / 后拼 /erp/forms。
     *
     * @param biz 集成业务配置
     * @param app 集成应用
     * @return 出站地址
     */
    private String resolveUrl(IntegrationBiz biz, IntegrationApp app) {
        String superNodeUrl = biz.getSuperNodeUrl();
        if (superNodeUrl != null && !superNodeUrl.isBlank()) {
            return superNodeUrl.trim();
        }
        String appUrl = app == null ? null : app.getAppUrl();
        if (appUrl == null || appUrl.isBlank()) {
            throw new BusinessException("超级节点未配置出站地址，且绑定的集成应用未配置访问地址");
        }
        String base = appUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + FORM_PATH;
    }

    /**
     * 构造出站请求体（JSON）。
     *
     * @param biz      集成业务配置
     * @param form     集成表单
     * @param purchase 采购单
     * @return JSON 字符串
     */
    private String buildBody(IntegrationBiz biz, IntegrationForm form, BookPurchase purchase) {
        Map<String, Object> payload = new LinkedHashMap<>(16);
        payload.put("formId", form.getId());
        payload.put("bizType", BIZ_TYPE_BOOK_PURCHASE);
        payload.put("purchaseId", purchase.getId());
        payload.put("purchaseNo", nullToEmpty(purchase.getPurchaseNo()));
        payload.put("bookName", nullToEmpty(purchase.getBookName()));
        payload.put("author", nullToEmpty(purchase.getAuthor()));
        payload.put("isbn", nullToEmpty(purchase.getIsbn()));
        payload.put("quantity", purchase.getQuantity() == null ? 1 : purchase.getQuantity());
        payload.put("unitPrice", purchase.getUnitPrice() == null ? BigDecimal.ZERO : purchase.getUnitPrice());
        payload.put("totalPrice", purchase.getTotalPrice() == null ? BigDecimal.ZERO : purchase.getTotalPrice());
        payload.put("applicantName", nullToEmpty(purchase.getApplicantName()));
        payload.put("superNodeName", nullToEmpty(biz.getSuperNodeName()));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException("构造 ERP 请求报文失败：" + summarize(e));
        }
    }

    /**
     * 解析 ERP 响应：要求 code=200 且 data.erpFormId 可转数字。
     *
     * @param body   响应报文
     * @param status HTTP 状态
     * @return ERP 表单号
     */
    private Long parseErpFormId(String body, int status) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("调用 ERP 创建表单失败：HTTP " + status + "，响应为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("调用 ERP 创建表单失败：HTTP " + status + "，响应不是合法 JSON");
        }
        JsonNode codeNode = root.get("code");
        if (codeNode == null || !codeNode.canConvertToInt() || codeNode.asInt() != ERP_CODE_SUCCESS) {
            String message = root.path("message").asText("");
            throw new BusinessException("调用 ERP 创建表单失败：ERP 返回 "
                    + (codeNode == null ? "未知状态" : codeNode.asText())
                    + (message.isBlank() ? "" : "（" + message + "）"));
        }
        JsonNode erpFormIdNode = root.path("data").path("erpFormId");
        Long erpFormId = toLong(erpFormIdNode);
        if (erpFormId == null) {
            throw new BusinessException("调用 ERP 创建表单失败：响应缺少 data.erpFormId");
        }
        return erpFormId;
    }

    /**
     * 容错转数字：兼容 ERP 以数字或数字字符串返回表单号。
     *
     * @param node JSON 节点
     * @return 数字值，无法转换返回 null
     */
    private Long toLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            try {
                return new BigDecimal(text).longValue();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /**
     * 异常摘要：优先带原因，避免空指针类异常只有类型名。
     *
     * @param e 异常
     * @return 摘要文本
     */
    private String summarize(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + "：" + message;
    }

    /**
     * 报文截断，避免日志被超大响应刷爆。
     *
     * @param body 响应报文
     * @return 截断后的文本
     */
    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    /**
     * null 转空串（JSON 字段保持字符串类型）。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
