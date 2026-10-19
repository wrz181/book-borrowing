package com.example.bookborrowing.service.impl;

import com.example.bookborrowing.common.BusinessException;
import com.example.bookborrowing.common.PageResult;
import com.example.bookborrowing.entity.IntegrationApp;
import com.example.bookborrowing.entity.IntegrationBiz;
import com.example.bookborrowing.mapper.IntegrationAppMapper;
import com.example.bookborrowing.mapper.IntegrationBizMapper;
import com.example.bookborrowing.service.IntegrationBizService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 集成业务配置（超级节点配置）业务实现。
 * 一个业务类型只允许一条配置，业务类型唯一性由服务层校验 + 表唯一键 uk_biz_type 双重保证。
 */
@Service
public class IntegrationBizServiceImpl implements IntegrationBizService {

    /** 默认回调超时：1440 分钟 = 24 小时 */
    private static final int DEFAULT_CALLBACK_TIMEOUT_MINUTES = 1440;

    private final IntegrationBizMapper integrationBizMapper;

    private final IntegrationAppMapper integrationAppMapper;

    public IntegrationBizServiceImpl(IntegrationBizMapper integrationBizMapper,
                                     IntegrationAppMapper integrationAppMapper) {
        this.integrationBizMapper = integrationBizMapper;
        this.integrationAppMapper = integrationAppMapper;
    }

    @Override
    public IntegrationBiz getByBizType(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        return integrationBizMapper.selectByBizType(bizType.trim());
    }

    @Override
    public PageResult<IntegrationBiz> page(int pageNum, int pageSize, String bizType, String bizName, Integer enabled) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        long total = integrationBizMapper.selectCount(bizType, bizName, enabled);
        List<IntegrationBiz> list = total == 0
                ? List.of()
                : integrationBizMapper.selectPage(bizType, bizName, enabled,
                        (safePageNum - 1) * safePageSize, safePageSize);
        return new PageResult<>(list, total, safePageNum, safePageSize);
    }

    @Override
    public IntegrationBiz getById(Long id) {
        if (id == null) {
            return null;
        }
        return integrationBizMapper.selectById(id);
    }

    @Override
    public void create(IntegrationBiz biz) {
        if (biz == null) {
            throw new BusinessException("业务配置不能为空");
        }
        if (biz.getEnabled() == null) {
            biz.setEnabled(1);
        }
        if (biz.getCallbackTimeoutMinutes() == null) {
            biz.setCallbackTimeoutMinutes(DEFAULT_CALLBACK_TIMEOUT_MINUTES);
        }
        trimText(biz);
        validate(biz, null);
        integrationBizMapper.insert(biz);
    }

    @Override
    public void update(Long id, IntegrationBiz biz) {
        if (id == null) {
            throw new BusinessException("业务配置ID不能为空");
        }
        if (biz == null) {
            throw new BusinessException("业务配置不能为空");
        }
        IntegrationBiz exist = integrationBizMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("业务配置不存在");
        }
        biz.setId(id);
        // 未传字段沿用库中原值，避免全字段更新把配置清空（如编辑时未回传描述、超时）
        if (biz.getBizType() == null || biz.getBizType().isBlank()) {
            biz.setBizType(exist.getBizType());
        }
        if (biz.getBizName() == null || biz.getBizName().isBlank()) {
            biz.setBizName(exist.getBizName());
        }
        if (biz.getAppId() == null) {
            biz.setAppId(exist.getAppId());
        }
        if (biz.getSuperNodeName() == null || biz.getSuperNodeName().isBlank()) {
            biz.setSuperNodeName(exist.getSuperNodeName());
        }
        if (biz.getSuperNodeUrl() == null) {
            biz.setSuperNodeUrl(exist.getSuperNodeUrl());
        }
        if (biz.getCallbackTimeoutMinutes() == null) {
            biz.setCallbackTimeoutMinutes(exist.getCallbackTimeoutMinutes());
        }
        if (biz.getEnabled() == null) {
            biz.setEnabled(exist.getEnabled());
        }
        if (biz.getDescription() == null) {
            biz.setDescription(exist.getDescription());
        }
        trimText(biz);
        validate(biz, id);
        integrationBizMapper.updateById(biz);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException("业务配置ID不能为空");
        }
        if (integrationBizMapper.selectById(id) == null) {
            throw new BusinessException("业务配置不存在");
        }
        // 物理删除（DELETE）：删除后同一 biz_type 可重新新增，唯一键 uk_biz_type 不再被历史行占用
        integrationBizMapper.deleteById(id);
    }

    /**
     * 校验业务配置字段：业务类型非空且唯一、绑定应用存在且启用、超级节点名称非空、回调超时大于0。
     *
     * @param biz       业务配置实体
     * @param excludeId 唯一性校验需排除的ID（新增传 null，修改传自身ID）
     */
    private void validate(IntegrationBiz biz, Long excludeId) {
        if (biz.getBizType() == null || biz.getBizType().isBlank()) {
            throw new BusinessException("业务类型不能为空");
        }
        if (integrationBizMapper.countByBizType(biz.getBizType(), excludeId) > 0) {
            throw new BusinessException("业务类型[" + biz.getBizType() + "]已配置超级节点，一个业务类型只能配置一个");
        }
        if (biz.getBizName() == null || biz.getBizName().isBlank()) {
            throw new BusinessException("业务名称不能为空");
        }
        if (biz.getAppId() == null) {
            throw new BusinessException("绑定的集成应用不能为空");
        }
        IntegrationApp app = integrationAppMapper.selectById(biz.getAppId());
        if (app == null) {
            throw new BusinessException("绑定的集成应用不存在");
        }
        if (app.getEnabled() == null || app.getEnabled() != 1) {
            throw new BusinessException("绑定的集成应用已停用，请先启用该应用");
        }
        if (biz.getSuperNodeName() == null || biz.getSuperNodeName().isBlank()) {
            throw new BusinessException("超级节点名称不能为空");
        }
        if (biz.getCallbackTimeoutMinutes() == null || biz.getCallbackTimeoutMinutes() <= 0) {
            throw new BusinessException("回调超时时间必须大于0分钟");
        }
    }

    /**
     * 去除文本字段首尾空白，避免「同名不同类型」的脏数据绕过唯一性校验。
     *
     * @param biz 业务配置实体
     */
    private void trimText(IntegrationBiz biz) {
        if (biz.getBizType() != null) {
            biz.setBizType(biz.getBizType().trim());
        }
        if (biz.getBizName() != null) {
            biz.setBizName(biz.getBizName().trim());
        }
        if (biz.getSuperNodeName() != null) {
            biz.setSuperNodeName(biz.getSuperNodeName().trim());
        }
        if (biz.getSuperNodeUrl() != null && biz.getSuperNodeUrl().isBlank()) {
            // 出站地址留空表示由 app_url + /erp/forms 拼接，统一存 null 便于下游判空
            biz.setSuperNodeUrl(null);
        }
    }
}
