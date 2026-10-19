package com.example.bookborrowing.service;

import com.example.bookborrowing.dto.SsoTicketDTO;
import com.example.bookborrowing.entity.IntegrationApp;

import java.util.List;

/**
 * 单点登录应用集成业务接口。
 */
public interface IntegrationAppService {

    /**
     * 查询全部应用（集团管理员配置页）。
     *
     * @return 应用列表
     */
    List<IntegrationApp> listAll();

    /**
     * 查询已启用应用（前台动态栏目）。
     *
     * @return 应用列表
     */
    List<IntegrationApp> listEnabled();

    /**
     * 按ID查询应用。
     *
     * @param id 应用ID
     * @return 应用实体
     */
    IntegrationApp getById(Long id);

    /**
     * 新增应用。
     *
     * @param app 应用实体
     */
    void create(IntegrationApp app);

    /**
     * 修改应用。
     *
     * @param app 应用实体
     */
    void update(IntegrationApp app);

    /**
     * 删除应用（逻辑删除）。
     *
     * @param id 应用ID
     */
    void delete(Long id);

    /**
     * 生成单点登录 ticket。
     *
     * @param appId  应用ID
     * @param userId 用户ID
     * @return ticket 字符串
     */
    String generateTicket(Long appId, Long userId);

    /**
     * 校验并解析 ticket。
     *
     * @param ticket ticket 字符串
     * @return 解析结果，无效返回 null
     */
    SsoTicketDTO validateTicket(String ticket);
}
