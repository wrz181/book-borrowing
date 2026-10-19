# 调研：致远 OA 后台管理台形态（T1-2 产出）

> 来源：WorkBuddy 调研子代理（2026-10-19），供 sso_scope 子项 (c)「集成应用配置等迁后台」规划使用。
> 整体置信度：中高。

## Q1：独立 URL+独立登录，还是同一门户内的管理布局？
**两种形态并存**：
- 主形态（V5/A8-V5）：管理员用普通门户账号登录（同一登录页 `/seeyon/main.do`），然后**在同一门户内切换为管理员身份**进入后台管理。第三方文档给出的切换入口即 `{域名}/seeyon/main.do?method=main&fl=1&switchToAdmin=1`；实际运行日志旁证：`OrgRoleStdManagerImpl: switchToAdmin success ... roleName: SystemAdmin`。数据模型上，后台菜单与前台菜单同存于 `PRIV_MENU` 表（EXT1：0 前台/1 后台；EXT4：0 后台管理/1 前台应用），属同一权限资源体系。
- 另有系统级「管理控制台」独立入口 `/seeyon/management/index.jsp`（独立登录，用于系统管理员/三员级维护），与门户内「后台管理」是两回事。

## Q2：集成应用 / SSO 配置在后台的哪个菜单层级？
落在后台管理内的 **CIP 集成平台**板块。第三方接入文档（Authing 对接 A8v5）描述的路径为：切换集团管理员身份 → 进入 CIP 集成平台 → 「第三方应用」栏目 → 产品登记 / 应用注册 → 单点登录设置（启用、应用类型、认证模式=门户认证机制等）。知识库旁证：REST 资源 `ThirdpartyAdminSSOResource`（/cip）、`CipSSObtnLoginResource`（/cip/control）；数据库有「CIP单点登录配置表」（字段 SSO_LOGIN_URL、LOGIN_ACL「单点登录门户配置权限控制」）及「单点登录配置表」（PORTAL_ID 关联注册应用）。集成平台为独立模块 seeyon-cip-service。

## Q3：典型后台一级菜单（官方叫法）
- V5/A8-V5 管理员手册级清单（旧版手册旁证）：**组织机构**（部门/岗位/人员/权限管理）、**系统配置**（日志管理、登陆配置、菜单设置、组件管理）、**参数设置**、**实施工具**（组织机构导入导出）、**系统信息/单位信息**、**个人设置**；超级管理员另含「管理员设置、角色权限设置」。
- 新一代（V8 协同云官方文档）机构管理员后台模块：**组织模型、用户中心、应用中心、门户、流程管理、集成连接、数据报表、移动、基础设置**。
- 注意：两代叫法不同，未在知识库中找到 V5 后台一级菜单的权威完整清单（见不确定项）。

## Q4：访问控制模型
**按管理员角色分权，非单一超管**：
- 数据模型区分「集团管理员 / 单位管理员 / 系统管理员」及「三员角色人员」（USER_TYPE 字段；AGENT_OPERATION 区分被代理人/集团管理员/单位管理员）。SaaS 侧另有 SYSTEMADMIN、ADMINISTRATOR（单位管理员）标志位。
- 旧版手册明确超级管理员与系统管理员（单位级）菜单集不同；V8 文档区分「机构管理员」（完整后台权限）与「组织模型管理员」（仅组织模型模块），支持分级（根机构/下级机构）授权。
- 结论：后台菜单按角色授权裁剪，可授权给不同管理员角色；CIP SSO 配置还有独立 LOGIN_ACL 权限控制。

## 证据清单
1. 技能知识库 `references/analysis/05-database-schema.md:17281-17328`（PRIV_MENU 表，前台/后台菜单分类）— 官方表结构整理，置信度高
2. `05-database-schema.md:5439,5441`（SYSTEMADMIN/ADMINISTRATOR 标志）、`:13200,21428`（三员角色、集团/单位管理员）、`:19606-19620`（CIP 单点登录配置表 SSO_LOGIN_URL/LOGIN_ACL）、`:4137`（单点登录配置 PORTAL_ID）
3. `02-api-catalog.md:841-845`（ThirdpartyAdminSSOResource 等 /cip 资源）、`01-module-classification.md:250`（CIP 集成平台模块）
4. Authing 官方对接文档 docs.authing.cn（A8v5 后台入口 URL、CIP 集成平台内 SSO 配置路径）— 第三方但操作性文档
5. cloud.seeyon.com V8 官方文档（机构管理员后台模块清单）— 官方，但属 V8 产品线
6. bodocs.net《致远OA管理员操作手册》（超级/系统管理员菜单划分）— 第三方转载手册
7. E:\zhiyuan 运行日志 switchToAdmin/SystemAdmin 记录 — **旁证，非官方文档**

## 不确定项
- V5/A8-V5 后台一级菜单的**完整官方清单**未在知识库命中（知识库为源码分析库，不含 UI 菜单文档）。
- 「管理控制台 /seeyon/management/index.jsp」的菜单结构与三员的精确分工未查到权威文档。
- switchToAdmin 后的 URL/布局细节（是否独立 layout）未逐项确认。
