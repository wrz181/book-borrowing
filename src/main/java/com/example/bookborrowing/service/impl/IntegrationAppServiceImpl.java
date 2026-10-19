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
import java.util.UUID;

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
        if (app.getTodoEnabled() == null) {
            app.setTodoEnabled(1);
        }
        if (app.getMsgEnabled() == null) {
            app.setMsgEnabled(1);
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
        return new SsoTicketDTO(appId, userId);
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
}
