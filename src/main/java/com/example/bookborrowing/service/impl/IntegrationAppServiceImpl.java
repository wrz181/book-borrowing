package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.dto.SsoTicketDTO;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.mapper.IntegrationAppMapper;
import com.example.bookborrowing.service.IntegrationAppService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单点登录应用集成业务实现。
 * ticket 采用「Base64(载荷) + . + MD5 签名」结构，签名密钥为该应用的 ssoSecret。
 */
@Service
public class IntegrationAppServiceImpl implements IntegrationAppService {

    /** ticket 有效期：5 分钟 */
    private static final long TICKET_TTL_MILLIS = 5 * 60 * 1000L;

    /** 默认初始密码 */
    private static final String DEFAULT_SECRET_PREFIX = "SSO-";

    /** 已消费票据登记：ticket -> 该票据自身的过期时间（毫秒），票据过期后登记项即可清理 */
    private static final Map<String, Long> CONSUMED_TICKETS = new ConcurrentHashMap<>();

    /** 已消费票据清理间隔：每分钟最多清理一次，避免每次写入都全量扫描 */
    private static final long CONSUMED_SWEEP_INTERVAL_MILLIS = 60 * 1000L;

    /** 下一次允许清理的时间戳（毫秒），用 CAS 保证同一时刻只有一个线程清理 */
    private static final AtomicLong NEXT_CONSUMED_SWEEP_AT = new AtomicLong(0L);

    private final IntegrationAppMapper integrationAppMapper;

    public IntegrationAppServiceImpl(IntegrationAppMapper integrationAppMapper) {
        this.integrationAppMapper = integrationAppMapper;
    }

    @Override
    public List<IntegrationApp> listAll() {
        return integrationAppMapper.selectAll();
    }

    @Override
    public List<IntegrationApp> listEnabled() {
        return integrationAppMapper.selectEnabled();
    }

    @Override
    public IntegrationApp getById(Long id) {
        return integrationAppMapper.selectById(id);
    }

    @Override
    public void create(IntegrationApp app) {
        validate(app);
        if (app.getAppType() == null || app.getAppType().isBlank()) {
            app.setAppType("SSO");
        }
        if (app.getSsoSecret() == null || app.getSsoSecret().isBlank()) {
            app.setSsoSecret(DEFAULT_SECRET_PREFIX + UUID.randomUUID().toString().replace("-", ""));
        }
        if (app.getEnabled() == null) {
            app.setEnabled(1);
        }
        integrationAppMapper.insert(app);
    }

    @Override
    public void update(IntegrationApp app) {
        if (app.getId() == null) {
            throw new BusinessException("应用ID不能为空");
        }
        if (integrationAppMapper.selectById(app.getId()) == null) {
            throw new BusinessException("应用不存在");
        }
        validate(app);
        // 密钥不再回显，编辑时留空表示沿用原密钥
        if (app.getSsoSecret() == null || app.getSsoSecret().isBlank()) {
            IntegrationApp exist = integrationAppMapper.selectById(app.getId());
            app.setSsoSecret(exist == null ? null : exist.getSsoSecret());
        }
        integrationAppMapper.updateById(app);
    }

    @Override
    public void delete(Long id) {
        if (integrationAppMapper.selectById(id) == null) {
            throw new BusinessException("应用不存在");
        }
        integrationAppMapper.deleteById(id);
    }

    @Override
    public String generateTicket(Long appId, Long userId) {
        IntegrationApp app = integrationAppMapper.selectById(appId);
        if (app == null) {
            throw new BusinessException("应用不存在");
        }
        if (app.getEnabled() == null || app.getEnabled() != 1) {
            throw new BusinessException("应用已停用");
        }
        long expire = System.currentTimeMillis() + TICKET_TTL_MILLIS;
        String payload = appId + ":" + userId + ":" + expire;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(payload, app.getSsoSecret());
    }

    @Override
    public SsoTicketDTO validateTicket(String ticket) {
        ParsedTicket parsed = parseTicket(ticket);
        if (parsed == null) {
            return null;
        }
        // 已消费的票据一律视为无效：读接口在票据被消费后同样被拒
        if (isTicketConsumed(ticket)) {
            return null;
        }
        return new SsoTicketDTO(parsed.appId, parsed.userId);
    }

    @Override
    public void consumeTicket(String ticket) {
        ParsedTicket parsed = parseTicket(ticket);
        if (parsed == null) {
            throw new BusinessException("单点登录票据无效或已过期，请重新从图书借阅系统进入");
        }
        sweepConsumedTickets(System.currentTimeMillis());
        if (CONSUMED_TICKETS.putIfAbsent(ticket, parsed.expire) != null) {
            throw new BusinessException("单点登录票据已使用，请重新从图书借阅系统进入");
        }
    }

    @Override
    public boolean isTicketConsumed(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        Long expire = CONSUMED_TICKETS.get(ticket);
        if (expire == null) {
            return false;
        }
        if (expire < System.currentTimeMillis()) {
            // 票据本身已过期，登记项失去意义，顺手移除
            CONSUMED_TICKETS.remove(ticket, expire);
            return false;
        }
        return true;
    }

    /**
     * 校验格式、有效期、应用启用状态与签名（不判断是否已被消费）。
     *
     * @param ticket ticket 字符串
     * @return 解析结果，无效返回 null
     */
    private ParsedTicket parseTicket(String ticket) {
        if (ticket == null || ticket.isBlank() || !ticket.contains(".")) {
            return null;
        }
        int idx = ticket.lastIndexOf('.');
        String encoded = ticket.substring(0, idx);
        String signature = ticket.substring(idx + 1);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            return null;
        }
        Long appId;
        Long userId;
        long expire;
        try {
            appId = Long.parseLong(parts[0]);
            userId = Long.parseLong(parts[1]);
            expire = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (System.currentTimeMillis() > expire) {
            return null;
        }
        IntegrationApp app = integrationAppMapper.selectById(appId);
        if (app == null) {
            return null;
        }
        if (app.getEnabled() == null || app.getEnabled() != 1) {
            return null;
        }
        if (!sign(payload, app.getSsoSecret()).equals(signature)) {
            return null;
        }
        return new ParsedTicket(appId, userId, expire);
    }

    /**
     * 清理已过期票据的登记项，保证进程内结构不无界增长。
     *
     * @param now 当前时间戳（毫秒）
     */
    private void sweepConsumedTickets(long now) {
        long next = NEXT_CONSUMED_SWEEP_AT.get();
        if (now < next || !NEXT_CONSUMED_SWEEP_AT.compareAndSet(next, now + CONSUMED_SWEEP_INTERVAL_MILLIS)) {
            return;
        }
        CONSUMED_TICKETS.values().removeIf(expire -> expire == null || expire < now);
    }

    /**
     * 生成 MD5 签名。
     *
     * @param payload 载荷
     * @param secret  密钥
     * @return 十六进制签名
     */
    private String sign(String payload, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((payload + ":" + secret).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("ticket 签名失败");
        }
    }

    private void validate(IntegrationApp app) {
        if (app.getAppName() == null || app.getAppName().isBlank()) {
            throw new BusinessException("应用名称不能为空");
        }
        if (app.getAppUrl() == null || app.getAppUrl().isBlank()) {
            throw new BusinessException("应用地址不能为空");
        }
    }

    /**
     * ticket 解析结果，额外保留过期时间，供已消费票据登记项的清理使用。
     */
    private static final class ParsedTicket {

        private final Long appId;

        private final Long userId;

        private final long expire;

        private ParsedTicket(Long appId, Long userId, long expire) {
            this.appId = appId;
            this.userId = userId;
            this.expire = expire;
        }
    }
}
