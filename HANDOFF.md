# 图书借阅系统 · 任务交接单（当前在做什么 / 还要做什么）

> 用途：交接给 workbuddy、opencode 等协作方直接接手。
> 生成时间：本会话最新状态。所有结论均以**代码行号 / 数据库 / 实时接口探测**为准，不以文档自述为准。
> 工作目录：`E:\bookborrowing`

---

## 0. 环境与运行态（动手前必读）

| 项 | 值 |
| --- | --- |
| JDK | `E:\java\jdk-21.0.11`。**每条 pwsh 命令必须先设** `$env:JAVA_HOME='E:\java\jdk-21.0.11'; $env:Path="$env:JAVA_HOME\bin;$env:Path"`，否则报 `Fatal error compiling: 无效的标记: --release` |
| Maven | `E:\MAVEN\apache-maven-3.9.16\bin\mvn.cmd`，离线构建 `mvn -B -o -q compile` |
| 后端 | 8080，当前**已在后台运行**（job `pwsh-125`，`spring-boot:run`） |
| 前端 | `frontend/`，Vite 5173（pid 13936） |
| ERP 演示端 | `erp-frontend/`，零依赖 Node 静态服务 + `/api/**` 代理，5174（pid 1296） |
| 构建产物 | `frontend/dist/` 已构建，主 chunk `dist/assets/index-Cphus_E9.js` |

### 账号（密码统一 `123456`）
| 账号 | 角色 | 权限数 |
| --- | --- | --- |
| `admin` | SYS_ADMIN(1) 系统管理员 + SEC_AUDIT_ADMIN(6)（多身份，演示顶栏切换） | 6 / 3 |
| `groupadmin` | GROUP_ADMIN(2) 集团管理员 | 17 |
| `user` | NORMAL(3) 普通人员 | 5 |
| `audit_user01` | NORMAL | 5 |
| `sanyuan_sys` | SEC_SYS_ADMIN(4) 三员-系统管理员 | 5 |
| `sanyuan_sec` | SEC_SEC_ADMIN(5) 三员-安全保密管理员 | 5 |
| `sanyuan_audit` | SEC_AUDIT_ADMIN(6) 三员-审计管理员 | 3 |

### 硬约束（违反会造成返工或环境破坏）
1. **不要重启 8080**——除非用户本人在开发该后端；前端改动走 Vite build/HMR，不需要重启后端。
2. **只在本地开发**，绝不触碰服务器/生产环境。
3. 不要传 `sandbox_permissions`（本会话危险策略会直接拒绝审批）。
4. 全局异常处理器把 404/500 都包成 **HTTP 200 + body.code**；**只有权限拦截器返回真 HTTP 403**。判断结果要看 `code`，不能只看 HTTP 状态。
5. `E:\zhiyuan` 是**已安装的 A8 产品**（63,754 文件 / 约 7.4 GB），不是源码。只能作概念参考，不能从中拷贝 OA 的 SSO 代码；引用其结论须标「旁证，非官方文档」。
6. **重启后端必须显式设 `$env:SERVER_PORT='8080'`**：WorkBuddy 宿主进程监听 13309 且向子进程注入 `SERVER_PORT=13309`，Spring Boot 优先读环境变量导致绑错端口（2026-10-19 实测踩坑）。

---

## 1. 原始需求验收结论（六条）

需求原文出处：`docs/图书借阅系统-集团管理员与系统管理员扩展方案.md` L27–L45。

| # | 需求 | 状态 | 关键证据 |
| --- | --- | --- | --- |
| ① | 图书采购链：采购 → 票据 → ERP → 表单 → 待办+消息 → 库存自动建档 | ✅ 完成 | `BookPurchaseServiceImpl` L92 建采购单、L129 查应用、L145 `integrationFormMapper.insert` 下发表单、L157 `stockIn`、L164 回读表单、L177 命中已有图书「库存 +N 册」、L188 `bookMapper.insert` 未命中则新建、L196 `markStockIn`、L202–207 采购完成消息；`ErpIntegrationServiceImpl` L165 `bizType="INTEGRATION_FORM"`。实时探测 `GET /api/purchases/page` code=200 total=6 |
| ② | 集团管理员 SSO 应用配置 | ✅ 完成 | `IntegrationAppController` L24 类级 `group:app`；`SsoController` L35 `/apps`、L50–52 `/ticket` 带 `@RequirePermission("group:app")`；前端 `Layout.vue` L31/L63/L65/L71/L128/L149、`api/sso.js`、`views/admin/group/Apps.vue`。**实测**：`groupadmin` 领票 200（ticket 长 56，validate 200）；`admin`/`user` 领票 HTTP 403 `无操作权限：group:app` |
| ③ | ERP 填写回传待办 | ✅ 完成 | `OpenIntegrationController` L29（`/api/open/**` 不走 JWT）、L54 票据校验；`ErpIntegrationServiceImpl` L58 `submitForm`、L77/L118 `notifySender`、L110「无权处理其他应用的业务表单」、L114「表单已被处理」、L141「票据无效或已过期」；表 `integration_form` 当前 4 条 |
| ④ | 消息推送 | ✅ 完成 | `MessageController` L22 类级 `group:message`；`ErpIntegrationServiceImpl` L155–178 `notifySender` 同时写 `todo_item` + `message_item`；`BookPurchaseServiceImpl` L202–207 采购完成消息 `msgType="PURCHASE"`。实时探测 `GET /api/messages/page` code=200 total=8 |
| ⑤ | 集团管理员**仅能建普通账号**；NORMAL 不能发 ERP 表单、不能加书 | ⚠️ **部分达成（有提权漏洞）** | NORMAL 侧已封死：实测 `user` 对 `group:user`/`group:app`/`group:todo`/`purchase:view`/`POST /api/books` **全部 403**（`sql/upgrade-rbac-group.sql` L201–208 只给 5 个权限）。但**集团管理员建账号无角色限制**，见 §3 缺陷 D3 |
| ⑥ | 系统管理员：日志类型设置 / 内存监控 / JS 缓存清理 | ✅ 完成（文档描述过期） | `SystemAdminController` L37 `@RequestMapping("/api/admin/system")`；`system:log` L56–57/L68–69/L85–86/L99–100；`system:monitor` L111–112；`system:cache` L181–182/L192–193。**实测** `admin`：`/logs/modules`、`/memory`、`/cache/version` 全 code=200（返回真实模块列表 / 堆内存 1.6% / 版本戳 1792390355918）；`groupadmin` 三项全 HTTP 403 |

> 注意：`docs` L163 §6.3 仍写「系统管理员分组本期先在菜单中占位（灰显）」——**已过期**，⑥ 实际早已实现并可访问。

---

## 2. 已完成并已验证的工作（本会话产出）

### 2.1 缺陷 D1 已修复：停用应用仍可通行
- 问题：`verify()` / `validateTicket()` 忽略 `enabled` 字段，应用停用后 `/api/open/todo` 与票据校验仍放行。
- 修复：停用应用校验前移，`enabled != 1` 直接抛 `BusinessException("应用已停用")`。
- 复验：停用后 → `code=400`，且 `GET /api/sso/apps` 不再返回该应用；重新启用 → 恢复 200。

### 2.2 缺陷 D2 已修复：领票接口无权限门禁（越权）
- 问题：`SsoController.ticket` 原本**无任何权限校验**，NORMAL 账号 `user` 可自行领票并直接向 ERP 提交表单（已实测提交成功，formId=8 已清理）。
- 修复：`SsoController.java` L51 增加 `@RequirePermission("group:app")`；前端 `Layout.vue` L31/L128 用同一权限点控制「集成应用」栏目显隐。
- 权限点选择过程：最初用 `purchase:manage`，但该权限 `admin`(SYS_ADMIN) 也有，违背「仅集团管理员才有集成应用配置」→ **收敛为 `group:app`**（唯一持有者 GROUP_ADMIN）。
- 复验：`groupadmin` 200 / ticket len 56 / validate 200 / `hasGroupApp=true`；`admin` 与 `user` 均 403 / `hasGroupApp=false`。

### 2.3 文档同步已做
- `docs/…扩展方案.md` L105–L116 §5.4 增加 `group:app` 为「集成应用唯一入口权限」的说明（含 403 行为）。
- 同文件 L156 §6.2 ticket 行改为 `JWT + group:app`，并注明「仅集团管理员可领票，SYS_ADMIN 与 NORMAL 返回 HTTP 403」。
- 同文件 L390–392 §15.3 同步 ticket 接口的权限注解。
- `README.md` L178 / L221 / L222–225 同步 JWT 排除路径与 `/api/open/**` 接口表。

### 2.4 已实测确认（不需重做）
- 三类账号登录、权限数（6 / 17 / 5）、NORMAL 全部越权路径 403。
- ⑥ 三个系统管理接口真实返回数据，非占位。

### 2.5 缺陷 D3 已修复：集团管理员提权（2026-10-19，用户批准后实施）
- `SysUserServiceImpl.create/update` 增加角色白名单：目标角色必须 NORMAL，且被维护账号原角色也须 NORMAL（防止动管理员账号，含降级/提权）；注入 `SysRoleMapper` 查角色码，写法复用 `SysRoleServiceImpl` L52。
- 前端 `Users.vue` L185 角色下拉仅保留 `roleCode==='NORMAL'`；`GroupAdminUserController` javadoc 同步（Vite dev HMR 已生效，dist 未重建）。
- 复验（8080 重启后实测）：`groupadmin` 建 roleId=1 → HTTP 200 + `code=400`；NORMAL 账号改 roleId=1 → `code=400`；改管理员账号本身 → `code=400`；建 NORMAL 账号 → `code=200`（探针 probe_ok 已删除清理）。

### 2.6 缺陷 D4 已修复：secret 明文回显（2026-10-19）
- `IntegrationApp.ssoSecret` 加 `@JsonProperty(Access.WRITE_ONLY)`：所有接口出参不再回显，入参仍可写入（新增/换钥不受影响）。
- `IntegrationAppServiceImpl.update`：secret 留空则回填原值（`updateById` 是全字段 SET，必须显式回填）。
- 复验：`GET /api/admin/apps` JSON 不含 `ssoSecret` 与明文值；不带 secret 更新应用后，库内 `sso_secret` 长度仍 36，领票 len=56、`/api/open/sso/validate` code=200。
- 接口方法备忘：`/api/sso/ticket` 是 **GET**；`/api/open/sso/validate` 是 **POST**（仅 `ticket` 参数）。

### 2.7 sso_scope ②③ 已实现：三员切换 + 后台迁移（2026-10-19，方案1 多角色）
- **SQL**：`sql/upgrade-sanyuan.sql`（幂等）：`sys_user_role` 多对多表、3 个三员角色（SEC_SYS_ADMIN/SEC_SEC_ADMIN/SEC_AUDIT_ADMIN）及默认权限、存量 `role_id` 迁移、三员测试账号、admin 追加审计员身份（演示切换，可删）。
- **后端**：`SysUserRoleMapper`(+XML)；`SysPermissionMapper.selectPermCodesByUserId` 改为**多角色并集**、新增 `selectPermCodesByUserIdAndRole`；`PermissionCache` 键升级为字符串（`u:{id}` / `u:{id}:r:{roleId}`）；`PermissionInterceptor` 支持 `X-Active-Role` 请求头（校验角色属于该用户，伪造 403）；登录/`/auth/info` 返回 `roles + activeRoleId + permissions(按当前身份)`；`UserSaveDTO` 增加 `roleIds` 列表，create/update 整体替换 `sys_user_role`（D3 白名单作用于**每个** roleId）。
- **前端**：store 增加 `roles/activeRoleId`（localStorage 持久化）；`request.js` 全局附带 `X-Active-Role`；`Layout.vue` 顶栏身份切换下拉（多身份时显示）+「后台管理」入口，门户内旧「集团管理员/系统管理员」菜单组移除（待办/消息保留门户）；新增 `/backend/**` 独立路由组 + `views/backend/BackendLayout.vue`（账号管理/角色权限/应用集成/日志/内存/缓存），复用原视图文件，旧 `/admin/users|roles|apps|system/*` 路由已删除（无双入口）。
- **复验（8080 实测）**：admin 登录返回双身份、默认 6 权限；切 `X-Active-Role=审计员` 后权限仅 system:*，访问 books 403、默认身份 200；伪身份头 403；`sanyuan_sys` 可进账号管理、内存监控 403；`sanyuan_audit` 反之；D3 回归 `code=400`；`npm run build` exit 0（dev 5173 HMR 已生效）。

---

## 3. 未修复的已知缺陷（按优先级）

### D3【P0 · 安全提权】集团管理员可创建/修改出 SYS_ADMIN 账号（✅ 已修复，见 §2.5）
- **实测证据**：
  - `POST /api/admin/users`，body `{"username":"audit_probe01","nickname":"审计探针","roleId":1,"password":"123456"}`，以 `groupadmin` 身份 → `code=200 success`（roleId=1 即 SYS_ADMIN）。
  - `PUT /api/admin/users/7`，body `{"nickname":"审计探针2","roleId":1}`（该账号原 roleId=3）→ `code=200 success`，重查确认 roleId 已变 1。
  - 两个探针账号已删除，用户列表恢复为 `audit_user01, user, groupadmin, admin`。
- **根因**：
  - `SysUserServiceImpl.java` `create()` L67–87 只校验「登录账号不能为空 / 请选择账号角色 / 登录账号已存在」，随后 L82 `user.setRoleId(dto.getRoleId())` 原样落库，L86 `insert`。
  - 同文件 `update()` L89–108 只校验「用户ID不能为空 / 账号不存在 / 请选择账号角色」，L104 直接 set roleId，L105 `updateById`，L107 `clearCache()`。
  - 两条路径**都没有角色编码白名单**。
  - 前端 `views/admin/group/Users.vue` L9（查询下拉）与 L64–66（新增/编辑下拉）都渲染**未过滤的全量角色列表**（L185 `roles.value = await listRoles()`）。
  - `GroupAdminUserController.java` L55 javadoc 自己写着「新增账号（普通人员/管理员）」，即把越权当成了功能。
- **与需求冲突**：`docs` L34 原文是「账号与权限  新增**普通人员账号** + 给角色配置权限」；L128 明确「建议**只能配置 NORMAL 角色**，两个内置管理员角色锁定不可改（防止自我提权/互相提权）」。当前实现与该设计意图相反。
- **建议修法**（尚未实施，需用户确认后执行）：
  - 后端：`SysUserServiceImpl.create/update` 增加目标角色校验，非 NORMAL 角色的 roleId 拒绝（或仅允许 `is_builtin != 1`）。可复用 `SysRoleServiceImpl` L51–55 已有写法（那里已对内置角色权限做了 `!"NORMAL".equals(role.getRoleCode())` 锁定）。
  - 前端：`Users.vue` L185 处对 `roles` 结果过滤，只保留 `roleCode === 'NORMAL'` 供选择。
  - 验收：`groupadmin` 建 roleId=1 返回业务错误；`PUT` 把普通账号改成 roleId=1 也返回业务错误；`groupadmin` 仍能正常建 NORMAL 账号。

### D4【P1 · 密钥泄漏】`GET /api/admin/apps` 明文回传 `ssoSecret`（✅ 已修复，见 §2.6）
- **实测**：`groupadmin` 请求 `GET /api/admin/apps` → 返回 `ssoSecret=SSO-267451d7b0dd444bb3bb086ed78b80c2` 明文。对比 `GET /api/sso/apps` 已正确置空。
- **根因**：`entity/IntegrationApp.java` 的 `ssoSecret` 字段缺 `@JsonIgnore`（`SsoController` 侧是手工置 null 绕过的，配置接口没绕）。
- **修法建议**：字段加 `@JsonIgnore`，或配置接口返回 DTO 并把 secret 脱敏/掩码；编辑保存时若 secret 为空则保留原值。
- **验收**：`GET /api/admin/apps` 不再出现明文 secret；前端 `Apps.vue` 编辑仍可正常保存新密钥。

### D5【P2 · 仓库卫生】`src/main/resources/application.yml` 内 Git 跟踪的明文数据库密码
- 当前数据库密码以明文写在配置里且已提交（此处脱敏）。
- **用户 2026-10-19 决定：暂不处理**（认为不设环境变量、保持现状即可），仅在验收报告中记录风险。

---

## 4. 当前正在做的任务（进行中，未收敛）

### 4.1 任务：`sso_scope` 扩展范围（L2 复杂度，必须走访谈 → 规划 → 派发 → 验收）
用户原始加码（原文）：
> 「仅集团管理员才有集成应用的配置，并且实现OA的三员切换，并且集成应用（配置单点的）等功能需要切换到后台」

拆成三件事：

| 子项 | 状态 | 说明 |
| --- | --- | --- |
| (a) 集成应用仅限集团管理员 | ✅ **已完成并实测** | 见 §2.2，门禁权限点 `group:app` |
| (b) OA「三员」切换 | ✅ **已实现并实测** | 见 §2.7：`sys_user_role` 多对多 + 3 个三员角色 + `X-Active-Role` 身份切换 |
| (c) 集成应用配置等迁到后台管理 | ✅ **已实现** | 见 §2.7：`/backend/**` 独立路由组 + BackendLayout，旧 `/admin/*` 管理入口已移除 |

### 4.2 访谈（R-G）进度
- 第 1 轮**已答复**：
  - `backend_shape` = 「本工程内新增独立后台路由组（如 `/backend/**`，独立布局）」← 已定。
  - `backend_scope` = 「具体你可以参考现在OA的实现。反正」← 用户授权**以现有 OA 实现为准**推导迁移清单，然后向其确认；**不再作为开放问题**。
- 第 2 轮**已答复**（2026-10-19，基于 §4.3 两份调研报告）：
  - **三员角色**：新增 3 个三员角色（系统管理员/安全保密管理员/审计管理员），复用现有 RBAC 权限集。
  - **切换形态**：**同账号顶栏切换**（用户两次选择均为此项，明确非误选；与 OA 官方「独立账号」形态不同，按用户决定执行）。
  - **迁移清单**：账号与角色权限、集成应用 SSO、系统管理三件套（日志类型/内存监控/JS 缓存）迁入 `/backend/**`；**待办/消息留门户**（用户原话：待办消息是 ERP 这类外部系统推到门户的，OA 的 CIP 只管 SSO/待办/消息的*配置*）。
  - **登录态**：共用 JWT + 权限/路由隔离（非独立登录页）。
- ⚠️ ~~进入 T3/T4 前须用户确认「理解一致」~~ **已确认（2026-10-19）**：多身份建模采用**方案1 账号绑多角色**（新增 `sys_user_role` 多对多，放弃 OA 互斥），已实现。

### 4.3 调研子代理状态（✅ 2026-10-19 已重派并完成）
两份范围收窄的调研已完成，全文见：
- `docs/调研-OA三员模型.md`：三员 = **系统管理员 / 安全保密管理员 / 审计管理员**（官方 A8 方案，置信度高）；三员为**独立互斥账号**（同一用户不得兼任两员），属可选安全特性需显式开启；「三员切换」**非官方术语**（官方无此说法，实际=三个账号各自登录）。
- `docs/调研-OA后台管理台.md`（置信度中高）：V5 主形态是**同一门户内切换管理员身份**进后台（`switchToAdmin`），另有独立「管理控制台」`/seeyon/management/index.jsp`；SSO 配置属后台 **CIP 集成平台**板块；后台菜单按管理员角色分权裁剪（非单一超管）。

### 4.4 重派调研时必须遵守
- 用 `seeyon-*` skills 取权威答案：`seeyon-docs`、`seeyon-open-platform`、`seeyon-custom-dev-solution`、`seeyon-custom-dev-guidelines`、`seeyon-code-generator`、`seeyon-plugin-generator`、`seeyon-cap4-flow-data`、`seeyon-db-sql`、`seeyon-skillhub`、`customctrl-dev`。
- `E:\zhiyuan` 只能作旁证，须标注「旁证，非官方文档」。
- 查不到就写「未查到」，**禁止编造 API/菜单名**。
- 交付格式：中文 Markdown，分节 ≤200 字 + 证据清单（skill / 文档 / `E:\zhiyuan` 路径+行号）+ 置信度 + 「不确定项」小节；**不写代码、不写实现方案、不改文件**。
- 拆小任务、限制检索面，避免再次撑爆上下文（上一个就是因此中断的）。

---

## 5. 待办任务清单（可直接派工）

### T1【必须先做】重派 OA 事实调研（✅ 2026-10-19 完成，报告见 §4.3）
1. **三员模型**：致远「三员」的官方角色命名与职责边界；「三员切换」在产品里到底指什么（登录选身份 / 同账号切换 / 不同账号）；是否强制「三权分立」（系统管理、安全保密、安全审计互斥）；普通账号与三员的关系。
2. **后台管理台形态**：是独立 URL + 独立登录 + 独立前端，还是同门户内的菜单分组；集成应用/单点登录配置落在后台哪个菜单层级；典型后台一级菜单清单；后台权限模型（仅超管 vs 按功能授权）。

### T2【阻塞于 T1】R-G 访谈第 2 轮（✅ 2026-10-19 已答复并确认方案1）
把 T1 的事实转成候选方案，用**一次** `ask_user_question` 提交全部问题，每题带稳定 id、简短 header、推荐项放第一并标「(推荐)」。至少要问：
- Q1 三员具体是哪三个角色、是否新增为 `sys_role` 记录（复用现有 RBAC，全库仅 `SysRoleServiceImpl` L52 一处角色码字面量，改造成本低）。
- Q2 「切换」的交互形态（顶栏身份切换 / 重新登录 / 新增独立入口）。
- Q3 从 OA 后台菜单推导出的**迁移清单**，逐项勾选：账号管理、角色权限、集成应用（单点登录）、待办中心、消息中心、日志类型设置、内存监控、JS 缓存清理、参数配置等——哪些迁到 `/backend/**`、哪些留在门户。
- Q4 后台是否独立登录态、是否需要与门户会话隔离。
- **用户明确说「理解一致」后**才可进入规划与实现。

### T3【阻塞于 T2】实现 ② OA 三员切换（✅ 2026-10-19 已实现并复验，见 §2.7）
- 边界（预计）：`sql/` 新增增量脚本（参考 `sql/upgrade-rbac-group.sql` 的幂等写法 L165–208）、`SysRole` 种子、`AuthController` L90–91 返回的 `roleId/roleCode` 消费、前端 `Layout.vue` 顶栏切换入口、`stores/user.js`。
- 注意：现有鉴权全部基于权限字符串，只有一处硬编码角色码，因此三员可以纯用「新角色 + 权限集」建模，不必改拦截器。

### T4【阻塞于 T2】实现 ③ 集成应用配置等迁到 `/backend/**`（✅ 2026-10-19 已实现，见 §2.7）
- 形态已定：本工程内新增独立后台路由组 + 独立布局（不是新工程）。
- 涉及：`frontend/src/router/index.js` L54–95 现有 `admin/*` 路由与 meta.perm、`views/admin/group/*.vue`、`views/admin/system/*.vue` 的搬迁或复用；`Layout.vue` 菜单分组；后端接口路径是否需要新前缀。
- 风险：搬迁期间两套入口并存会导致权限守卫（`router/index.js` L106–128）与菜单显隐（`Layout.vue` L121 `showGroup`）出现双重判断，需要一次性切换。

### T5【可与 T1 并行】修 D3 提权漏洞（P0）（✅ 2026-10-19 已修复并复验，见 §2.5）
见 §3 D3，改动面小（1 个 Service + 1 个 Vue），但**属于需求语义变更，需用户点头**。

### T6【可与 T1 并行】修 D4 secret 明文（P1）（✅ 2026-10-19 已修复并复验，见 §2.6）
见 §3 D4，纯技术修复，风险低。

### T7【收尾】交付中文验收报告「看下我的需求都做完没」
- 要求：逐条需求 + 文件行号证据 + 实测数据；覆盖 D1/D2 修复、D3/D4 未修、权限点从 `purchase:manage` 收敛到 `group:app` 的过程、文档过期点、D5 风险。
- 已具备的素材即本文件 §1–§4，可直接扩写成正文。

---

## 6. 测试残留数据（已披露，避免误判为脏数据）

- `book_purchase`：id=11、12（测试采购单）
- `book`：id=12、13（采购入库自动建档产生）
- `todo_item`：id=4、5、7–9
- `message_item`：id=6–9、12、13
- `integration_form`：现存 4 条（id=8、9 已删除清理）
- 探针账号 `audit_probe01` / `audit_probe02`：**已创建且已删除**，用户表已复原为 4 个账号
- `sys_user`：新增三员测试账号 `sanyuan_sys` / `sanyuan_sec` / `sanyuan_audit`（种子数据，非脏数据）
- `sys_user_role`：11 行（含 admin 追加的 SEC_AUDIT_ADMIN 演示身份，可在角色权限配置页收回）
- 工作区还存在 `frontend/dist/`（构建产物）、`frontend-dev.log`

---

## 7. 关键文件索引（定位用）

| 关注点 | 文件 | 行 |
| --- | --- | --- |
| 权限注解 + 拦截器 | `annotation/RequirePermission.java`、`config/PermissionInterceptor.java`、`service/SysPermissionService`(`clearCache`) | — |
| 登录返回角色 | `controller/AuthController.java` | L90 `roleId`、L91 `roleCode` |
| SSO 领票 | `controller/SsoController.java` | L35 `/apps`、L50–52 `/ticket` + `group:app` |
| 集成应用配置接口 | `controller/IntegrationAppController.java` | L24 类级 `group:app` |
| 集团管理员账号管理 | `controller/GroupAdminUserController.java` | L28 类级 `group:user`、L46–51 `/page`、L60–61 create、L73–75 update、L104–105 重置密码 |
| 账号业务逻辑（D3 根因） | `service/impl/SysUserServiceImpl.java` | create L67–87、update L89–108、delete L111–113 |
| 角色权限锁定（可复用写法） | `service/impl/SysRoleServiceImpl.java` | L46、L51–55（内置角色锁）、L61 clearCache |
| 系统管理员三功能 | `controller/SystemAdminController.java` | L37 前缀、L56–57、L68–69、L85–86、L99–100、L111–112、L181–182、L192–193 |
| ERP 回传/待办/消息 | `service/impl/ErpIntegrationServiceImpl.java` | L58、L77、L110、L114、L118、L141、L155–178 |
| 采购全链路 | `service/impl/BookPurchaseServiceImpl.java` | L92、L129、L145、L157、L164、L177、L188、L196、L202–207 |
| 开放接口（免 JWT） | `controller/OpenIntegrationController.java` | L29、L54 |
| D4 根因 | `entity/IntegrationApp.java` | `ssoSecret` 缺 `@JsonIgnore` |
| 权限/角色种子 | `sql/upgrade-rbac-group.sql` | L140、L150、L153、L156–158、L165–170、L176–208 |
| 前端请求封装 | `frontend/src/utils/request.js` | L15、L26–37、L40–51 |
| 前端路由守卫 | `frontend/src/router/index.js` | L54–95、L106–128 |
| 前端用户态 | `frontend/src/stores/user.js` | L22 `hasAny`、L25 `setLogin` |
| 菜单与集成应用栏目 | `frontend/src/views/Layout.vue` | L31、L63–71、L121、L128、L140、L149 |
| 账号管理页（D3 前端） | `frontend/src/views/admin/group/Users.vue` | L9、L64–66、L85、L133、L150、L185 |
| 系统管理 API 封装 | `frontend/src/api/system.js` | L5–35 |
| 需求与方案文档 | `docs/图书借阅系统-集团管理员与系统管理员扩展方案.md` | L27–45 需求、L105–116 §5.4、L118–129 §5.5、L144–156 §6.2、L158–163 §6.3（已过期）、L177–184 阶段四 |

---

## 8. 复现验证命令（可直接跑）

```powershell
# 登录拿 token
$tok = (Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post `
  -ContentType 'application/json' -Body '{"username":"groupadmin","password":"123456"}').data.token

# ⑥ 系统管理接口：admin 应 200，groupadmin 应 403
curl.exe -s -w " [%{http_code}]" 'http://localhost:8080/api/admin/system/memory' -H "Authorization: Bearer $tok"

# ② 领票门禁
curl.exe -s -w " [%{http_code}]" 'http://localhost:8080/api/sso/ticket?appId=1' -H "Authorization: Bearer $tok"

# D3 提权复现（用 groupadmin 身份，期望改完后应被拒）
curl.exe -s -w " [%{http_code}]" -X POST 'http://localhost:8080/api/admin/users' `
  -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' `
  -d '{"username":"probe_x","nickname":"x","roleId":1,"password":"123456"}'

# D4 secret 明文复现
curl.exe -s 'http://localhost:8080/api/admin/apps' -H "Authorization: Bearer $tok"
```

> 判读提醒：业务失败返回的是 `HTTP 200 + code!=200`；只有权限不足才是真 `HTTP 403`。中文值在 pwsh 控制台会乱码（UTF-8 误解码），判断请以 ASCII 字段（`code`/`roleCode`/`id`）为准。

---

## 9. 给接手方的三条纪律

1. **访谈没收敛不许写实现**：`sso_scope` 的 ②③ 属 L2，用户一旦说「理解一致」再动手，否则做出来的东西必返工。
2. **别把子代理的自我陈述当结论**：必须自己读改动 + 跑接口复验；本文件所有 ✅ 都是实测过的。
3. **别再重读一遍全项目**：按 §7 索引定位即可；上一轮调研子代理就是检索面过大而撑爆上下文中断的。
