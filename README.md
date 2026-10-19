# 图书借阅管理系统

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 项目名称 | 图书借阅管理 |
| 学生姓名 | 王睿智 |
| 学号 | 2310160131 |
| 班级 | 计算机(怀)231 |
| 项目编号 | P01-F2-R03 |
| 技术栈 | Spring Boot 3.2 + MyBatis + MySQL 8 + JWT + Vue 3 + Element Plus |
| JDK | 21 |

## 业务实体与数据表（共 4 张，其中 3 张核心业务表）

| 表名 | 说明 |
| --- | --- |
| `book_category` | 图书分类表（核心业务表 1） |
| `book` | 图书信息表（核心业务表 2，多对一关联 book_category） |
| `borrow_record` | 借阅记录表（核心业务表 3，多对一关联 book，与 book 构成一对多） |
| `sys_user` | 系统用户表（JWT 登录使用） |

表间关系：

- `book_category` 1 — N `book`（一个分类下有多本图书）
- `book` 1 — N `borrow_record`（一本图书有多条借阅记录，**本项目的核心一对多关系**）

## 数据库配置

- 数据库名称：`bookborrowing`（MySQL 8.0，字符集 utf8mb4）
- 初始化脚本：`sql/bookborrowing.sql`（含建库、建表、外键、测试数据，可直接导入）
- 连接配置位置：`src/main/resources/application.yml` 中 `spring.datasource`（默认 root / 123456，改成你的 MySQL 密码）
- 初始登录账号（密码均为 `123456`，BCrypt 加密存储）：
  - `admin` —— 系统管理员（SYS_ADMIN）：日志类型设置 / 内存监控 / JS 缓存清理 + 业务只读
  - `groupadmin` —— 集团管理员（GROUP_ADMIN）：账号管理 / 角色权限配置 / 应用集成配置 / 待办 / 消息 + 业务管理（含图书采购）
  - `user` —— 普通人员（NORMAL）：图书·分类·借阅只读 + 发起借阅 + 归还

导入方式：MySQL 命令行执行 `source sql/bookborrowing.sql;`，或用 Navicat / IDEA 数据库工具运行该脚本。

## 运行方式

> **⚠️ 第一步必做（否则登录报“系统异常：Access denied…”）：**
> 打开 `src/main/resources/application.yml`，把 `spring.datasource.password` 的 `123456` **改成你本机 MySQL 的 root 真实密码**（`username` 一般不用改）。
> MySQL 启动时不会连接数据库，密码错误要等到第一次查询才暴露，所以务必先改再启动。

### 1. 后端（端口 8080）

```bash
# 在项目根目录（pom.xml 所在目录）
mvn spring-boot:run
```

或直接用 IDEA 打开项目，运行 `com.example.bookborrowing.BookBorrowingApplication`。

### 2. 前端（端口 5173，开发模式）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 （Vite 已将 `/api` 代理到后端 8080）。

### 3. 单端口模式（教师检查推荐）

```bash
cd frontend
npm install
npm run build          # 产物在 frontend/dist
```

将 `frontend/dist` 目录下的所有文件复制到 `src/main/resources/static/` 下，重启后端，直接访问 http://localhost:8080 即可（Spring Boot 托管前端页面）。

### 4. 类ERP平台（模拟第三方系统，独立端口 5174）

```bash
cd erp-frontend
node server.js      # 零第三方依赖：Node 内置模块静态托管 + /api 反向代理
```

浏览器访问 http://localhost:5174 ，或从主系统左侧「集成应用 → 图书采购ERP」栏目录单点登录进入。
详见 [`erp-frontend/README.md`](erp-frontend/README.md)。

## 功能清单

### 基础功能（CRUD + 动态 SQL + 关联查询）

- 图书分类管理：新增、修改、删除、列表查询
- 图书信息管理：新增、修改、删除、分页列表查询
- 借阅记录管理：新增、修改、删除、分页列表查询、归还操作
- **动态条件查询（MyBatis 动态 SQL）**：
  - 图书查询：图书名称（模糊）、作者（模糊）、图书分类、状态，四个条件均可单独使用，也可组合使用
  - 借阅记录查询：图书名称（模糊）、借阅人姓名、借阅证号、状态、借阅日期范围，均可单独或组合使用，结果按借阅日期倒序
- **关联查询（多表 JOIN）**：
  - 图书列表关联 `book_category` 显示分类名称
  - 借阅记录列表关联 `book` 与 `book_category` 显示图书名称、作者、分类名称

### 个人指定功能（F2 一对多关联查询）

实现图书详情及其关联借阅记录的一对多查询与页面展示：从图书主对象查看该图书的全部借阅记录。

- 实现位置：
  - Mapper：`BookMapper.xml` 的 `selectDetailById`，使用 `<resultMap>` + `<collection>` 将图书主对象与其全部借阅记录组装为 `Book.borrowRecords`
  - Service：`BookServiceImpl.getDetail`
  - 接口：`GET /api/books/{id}`
  - 页面：图书列表点击「详情/借阅记录」→ 图书基本信息 + 该图书全部借阅记录表格（支持在详情页直接归还）

### 个人业务规则（R03 重复业务校验）

**同一借阅人、同一图书、同一借阅日期不得重复生成借阅记录。**

- 实现位置：`BorrowRecordServiceImpl.validateAndPrepare`，调用 `BorrowRecordMapper.countByBorrowerBookDate` 校验，重复时抛出「同一借阅人同一天不能重复借阅同一本图书」
- 附加业务保护（属于同一业务规则的合理延伸）：
  - 同一借阅人对同一图书存在未归还记录时，不允许重复借阅
  - 借阅时校验图书库存，库存不足不允许借阅；借阅扣减库存、归还/删除未归还记录恢复库存
  - 存在借阅记录的图书不允许删除；存在图书的分类不允许删除

## 项目结构

```
bookborrowing/
├── pom.xml                          # Maven 配置
├── sql/
│   ├── bookborrowing.sql            # 数据库初始化脚本（建库建表 + 测试数据）
│   ├── upgrade-rbac-group.sql       # 增量脚本：RBAC 权限体系 + 集团管理员相关表
│   └── upgrade-purchase.sql         # 增量脚本：图书采购业务模块
├── src/main/java/com/example/bookborrowing/
│   ├── BookBorrowingApplication.java    # 启动类
│   ├── annotation/                       # @RequirePermission 权限注解
│   ├── common/                           # 统一响应、分页、业务异常、全局异常处理、日志缓冲、权限缓存
│   ├── config/                           # Web MVC 配置、JWT 拦截器、权限拦截器
│   ├── util/                             # JWT 工具类
│   ├── entity/                           # 实体类（图书·分类·借阅 + 角色·权限·应用·表单·待办·消息·采购）
│   ├── dto/                              # 查询条件与请求参数对象
│   ├── mapper/                           # MyBatis Mapper 接口
│   ├── service/                          # 业务层（接口 + impl）
│   └── controller/                       # Spring MVC 控制层
├── src/main/resources/
│   ├── application.yml               # 应用配置（数据源 / MyBatis / JWT）
│   └── mapper/                       # MyBatis XML（动态 SQL / 关联查询）
├── frontend/                         # Vue 3 + Element Plus 前端工程（主系统，端口 5173）
│   └── src/
│       ├── api/                      # 接口封装
│       ├── directives/               # v-permission 按钮级权限指令
│       ├── router/ stores/ utils/    # 路由 / 状态 / axios 封装
│       └── views/                    # 登录、布局、分类、图书、借阅、采购、集团管理员、系统管理员页面
├── erp-frontend/                     # 类ERP平台（模拟第三方系统，独立端口 5174，零依赖）
│   ├── server.js                     # 静态托管 + /api 反向代理
│   └── public/                       # 原生 HTML/CSS/JS 单页应用
└── docs/
    └── 图书借阅系统-集团管理员与系统管理员扩展方案.md   # 扩展方案与实施进展
```

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录（签发 JWT） |
| GET | /api/auth/info | 当前登录用户 |
| GET | /api/categories | 分类列表 |
| POST | /api/categories | 新增分类 |
| PUT | /api/categories/{id} | 修改分类 |
| DELETE | /api/categories/{id} | 删除分类 |
| GET | /api/books/page | 图书分页动态查询 |
| GET | /api/books/{id} | 图书详情 + 全部借阅记录（一对多） |
| POST | /api/books | 新增图书 |
| PUT | /api/books/{id} | 修改图书 |
| DELETE | /api/books/{id} | 删除图书 |
| GET | /api/borrow-records/page | 借阅记录分页动态查询 |
| GET | /api/borrow-records/{id} | 借阅记录详情 |
| POST | /api/borrow-records | 新增借阅记录 |
| PUT | /api/borrow-records/{id} | 修改借阅记录 |
| DELETE | /api/borrow-records/{id} | 删除借阅记录 |
| PUT | /api/borrow-records/{id}/return | 归还图书 |

除 `/api/auth/login` 与 `/api/open/**`（外系统回调，改用单点登录票据或应用密钥校验）外，所有接口均需在请求头携带 `Authorization: Bearer <token>`。

## 扩展模块：集团管理员 / 系统管理员 / 外系统集成

> 完整方案与实施进展见 [`docs/图书借阅系统-集团管理员与系统管理员扩展方案.md`](docs/图书借阅系统-集团管理员与系统管理员扩展方案.md)。

### 1. 权限体系（RBAC）

- 表：`sys_role` / `sys_permission` / `sys_role_permission`，`sys_user.role_id` 关联角色；
- 后端：`@RequirePermission("xxx")`（可标注类或方法）+ `PermissionInterceptor` 强校验，无权限返回 **HTTP 403**；
- 登录返回 `roleCode / roleName / permissions[]`，前端据此渲染菜单（`v-if`）与按钮（`v-permission`）；
- 内置角色 `SYS_ADMIN` / `GROUP_ADMIN` 权限锁定，仅 `NORMAL` 可在页面勾选配置。

### 2. 集团管理员控制台

| 模块 | 页面路由 | 权限点 |
| --- | --- | --- |
| 账号管理（新增普通人员、启用/禁用、重置密码） | `/admin/users` | `group:user` |
| 角色权限配置 | `/admin/roles` | `group:role` |
| 应用集成配置（单点登录 / 待办 / 消息） | `/admin/apps` | `group:app` |
| 待办中心（含菜单徽标） | `/admin/todos` | `group:todo` |
| 消息中心（含菜单徽标） | `/admin/messages` | `group:message` |

### 3. 图书采购业务模块（完整闭环）

页面 `/purchases`（权限 `purchase:view` / `purchase:manage`），状态机：`0 草稿 → 1 已提交 → 2 已入库`，另有 `9 已取消`。

流程：新增采购单 → 提交（可下发到指定集成应用）→ 外部平台办理回执 → 本系统「入库」
→ 按 **ISBN 优先、否则图书名称** 匹配已有图书增加库存，未匹配则新增图书；入库后向申请人发送消息。

### 4. 系统管理员控制台

| 模块 | 页面路由 | 权限点 | 实现 |
| --- | --- | --- | --- |
| 日志类型设置（按模块设级别，展示级别 ≥ 所选） | `/admin/system/logs` | `system:log` | Logback 运行时 `setLevel` + 内存环形缓冲 Appender |
| 内存监控 | `/admin/system/memory` | `system:monitor` | `ManagementFactory` 采集堆/非堆/内存池/GC/线程 |
| JS 缓存清理 | `/admin/system/cache` | `system:cache` | 递增静态资源版本号 + 前端清缓存重载 |

### 5. 类ERP平台集成（独立端口）

| 方法 | 路径 | 校验方式 | 说明 |
| --- | --- | --- | --- |
| GET | /api/sso/apps | JWT | 已启用应用（集成应用栏目，仅集团管理员可见） |
| GET | /api/sso/ticket?appId= | JWT + `group:app` | 生成单点登录票据（5 分钟有效）；仅集团管理员可领票 |
| POST | /api/open/sso/validate?ticket= | 票据 | 票据换登录用户信息 |
| GET | /api/open/forms?ticket=&status= | 票据 | 本应用集成表单列表 |
| POST | /api/open/forms/{id}/handle | 票据 | 办理表单 → 生成待办 + 消息 |
| POST | /api/open/form/submit | 票据 | 回传表单 → 生成待办 + 消息 |
| POST | /api/open/todo | AppId + Secret | 外系统推送待办 |
| POST | /api/open/message | AppId + Secret | 外系统推送消息 |

采购接口：`GET /api/purchases/page`、`GET /api/purchases/{id}`（`purchase:view`）；
`POST /api/purchases`、`PUT /api/purchases/{id}`、`DELETE /api/purchases/{id}`、
`PUT /api/purchases/{id}/submit`、`PUT /api/purchases/{id}/stock-in`、`PUT /api/purchases/{id}/cancel`（`purchase:manage`）。
