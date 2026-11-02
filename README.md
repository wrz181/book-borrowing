# 图书借阅管理系统

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 项目名称 | 图书借阅管理 |
| 学生姓名 | 王睿智 |
| 学号 | 2310160131 |
| 班级 | 计算机(怀)231 |
| 项目编号 | P01-F2-R03 |
| 技术栈 | Spring Boot 3.2 + MyBatis + MySQL 8 + JWT + Vue 3 + Element Plus（另附安卓端：Kotlin + Compose + Retrofit） |
| JDK | 17 |

## 业务实体与数据表（共 8 张：4 张核心业务表 + 4 张辅助功能表）

| 表名 | 说明 |
| --- | --- |
| `book_category` | 图书分类表（核心业务表 1） |
| `book` | 图书信息表（核心业务表 2，多对一关联 book_category） |
| `borrow_record` | 借阅记录表（核心业务表 3，多对一关联 book，与 book 构成一对多） |
| `sys_user` | 系统用户表（JWT 登录使用） |
| `book_comment` | 图书评论表（学生对图书评分留言，辅助功能表） |
| `sys_notice` | 系统公告表（管理员发布、首页展示，辅助功能表） |
| `search_history` | 搜索历史表（图书搜索自动留痕，辅助功能表） |
| `operation_log` | 操作日志表（借阅关键操作自动审计，辅助功能表） |

表间关系：

- `book_category` 1 — N `book`（一个分类下有多本图书）
- `book` 1 — N `borrow_record`（一本图书有多条借阅记录，**本项目的核心一对多关系**）
- `book` 1 — N `book_comment`（一本图书有多条评论）
- `sys_user` 1 — N `book_comment` / `search_history` / `operation_log`（用户产生的评论、搜索与操作记录）

## 数据库配置

- 数据库名称：`bookborrowing`（MySQL 8.0，字符集 utf8mb4）
- 初始化脚本：`sql/bookborrowing.sql`（含建库、建表、外键、测试数据，可直接导入）
- 连接配置位置：`src/main/resources/application.yml` 中 `spring.datasource`（默认 root / 123456，改成你的 MySQL 密码）
- 初始登录账号（密码均为 `123456`，BCrypt 加密存储）：
  - `admin` —— 管理员（role=admin）：全部数据的增删改查 + 借阅审批
  - `student` —— 学生（role=student，张三）：申请阅览、归还本人图书，只能查看本人借阅记录

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

### 4. 安卓端（可选演示，Kotlin + Jetpack Compose）

用 Android Studio 打开 `D:\AndroidStudioProjects\BookBorrowing`（如在本机另有目录则以实际为准），启动模拟器后直接 Run。
`data/remote/Constants.kt` 中 `BASE_URL` 默认 `http://10.0.2.2:8080/`（模拟器访问宿主机后端）；真机调试请改成电脑局域网 IP。

## 功能清单

### 基础功能（CRUD + 动态 SQL + 关联查询）

- 图书分类管理：新增、修改、删除、列表查询（支持按名称/修改时间排序）
- 图书信息管理：新增、修改、删除、分页列表查询
- 借阅记录管理：阅览申请、管理员确认/驳回、归还申请与确认、分页列表查询
  - 状态流转：`0 待确认 →（确认）1 借阅中 →（申请归还）4 归还待确认 →（确认）2 已归还`；`0 → 3 已驳回`、`4 → 1`（驳回归还）
  - 学生只能申请阅览与归还；**管理员确认后才扣减库存**，归还确认后自动恢复库存
- **动态条件查询（MyBatis 动态 SQL）**：
  - 图书查询：图书名称（模糊）、作者（模糊）、图书分类、状态，四个条件均可单独使用，也可组合使用
  - 借阅记录查询：图书名称（模糊）、借阅人姓名、借阅证号、状态、借阅日期范围，均可单独或组合使用，结果按借阅日期倒序
- **关联查询（多表 JOIN）**：
  - 图书列表关联 `book_category` 显示分类名称
  - 借阅记录列表关联 `book` 与 `book_category` 显示图书名称、作者、分类名称
- **辅助功能**：
  - 图书评论：图书详情页发表/删除评分评论（`book_comment`）
  - 系统公告：管理员发布公告，登录后首页顶部展示最新一条（`sys_notice`）
  - 搜索历史：图书动态搜索时自动记录关键字，可查本人最近 10 条（`search_history`）
  - 操作日志：提交申请/确认借阅/确认归还等关键操作自动留痕，管理员可审计（`operation_log`）

### 个人指定功能（F2 一对多关联查询）

实现图书详情及其关联借阅记录的一对多查询与页面展示：从图书主对象查看该图书的全部借阅记录。

- 实现位置：
  - Mapper：`BookMapper.xml` 的 `selectDetailById`，使用 `<resultMap>` + `<collection>` 将图书主对象与其全部借阅记录组装为 `Book.borrowRecords`
  - Service：`BookServiceImpl.getDetail`
  - 接口：`GET /api/books/{id}`
  - 页面：图书列表点击「详情/借阅记录」→ 图书基本信息 + 该图书全部借阅记录表格（支持在详情页直接发起归还）

### 个人业务规则（R03 重复业务校验）

**同一借阅人、同一图书、同一借阅日期不得重复生成借阅记录。**

- 实现位置：`BorrowRecordServiceImpl.validateAndPrepare`，调用 `BorrowRecordMapper.countByBorrowerBookDate` 校验，重复时抛出「同一借阅人同一天不能重复借阅同一本图书」
- 附加业务保护（属于同一业务规则的合理延伸）：
  - 同一借阅人对同一图书存在未归还记录时，不允许重复借阅
  - 借阅时校验图书库存，库存不足不允许借阅；**管理员确认借阅时扣减库存**，确认归还/删除未归还记录时恢复库存
  - 库存扣到 0 时图书状态自动置为「已下架/已借完」，归还后自动恢复「在架可借」
  - 存在未归还借阅记录的图书不允许删除；存在图书的分类不允许删除

### 借阅审批流程（学生 ↔ 管理员）

| 步骤 | 操作人 | 动作 | 结果 |
| --- | --- | --- | --- |
| 1 | 学生 | 图书信息页「申请阅览」 | 生成 `待确认` 记录（**不扣库存**） |
| 2 | 管理员 | 借阅记录页「确认借阅」/「驳回」 | 通过：`借阅中` + 扣库存（库存为 0 自动置图书不可借）；驳回：`已驳回` |
| 3 | 学生 | 「申请归还」 | `归还待确认` |
| 4 | 管理员 | 「确认归还」/「驳回归还」 | 通过：`已归还` + 库存 +1 + 图书恢复可借；驳回：回到 `借阅中` |

> 角色说明：登录用户带 `role` 字段（`admin`/`student`）。学生只能查看/操作 `borrower_id` 为本人的借阅记录（服务端按登录态收敛数据范围）；确认借阅、驳回、确认归还、驳回归还、借阅记录修改/删除仅管理员可执行。

## 项目结构

```
bookborrowing/
├── pom.xml                          # Maven 配置（Spring Boot 3.2 + MyBatis + JWT + BCrypt）
├── sql/
│   └── bookborrowing.sql            # 数据库初始化脚本（建库建表 + 外键 + 测试数据，可直接导入）
├── src/main/java/com/example/bookborrowing/
│   ├── BookBorrowingApplication.java    # 启动类
│   ├── common/                          # 统一响应 Result、分页 PageResult、业务异常、全局异常处理
│   ├── config/                          # Web MVC 配置（跨域）、JWT 登录拦截器
│   ├── util/                            # JWT 工具类
│   ├── entity/                          # 实体类（图书分类、图书、借阅记录、系统用户）
│   ├── dto/                             # 查询条件与登录参数对象
│   ├── mapper/                          # MyBatis Mapper 接口
│   ├── service/                         # 业务层（接口 + impl）
│   └── controller/                      # Spring MVC 控制层（认证 + 三个业务模块）
├── src/main/resources/
│   ├── application.yml               # 应用配置（数据源 / MyBatis / JWT）
│   └── mapper/                       # MyBatis XML（动态 SQL / 一对多关联查询）
├── frontend/                         # Vue 3 + Element Plus 前端工程（端口 5173）
│   └── src/
│       ├── api/                      # 接口封装（auth / book / category / borrowRecord）
│       ├── router/ stores/ utils/    # 路由 / Pinia 状态 / axios 封装
│       └── views/                    # 登录、布局、图书列表、图书详情、分类、借阅记录页面
└── docs/                             # 课程项目报告等文档
```

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录（签发 JWT） |
| GET | /api/auth/info | 当前登录用户 |
| GET | /api/categories | 分类列表（支持排序参数） |
| POST | /api/categories | 新增分类 |
| POST | /api/categories/{id} | 修改分类 |
| POST | /api/categories/{id}/delete | 删除分类（逻辑删除，分类下有图书时拒绝） |
| GET | /api/books/page | 图书分页动态查询（四条件组合） |
| GET | /api/books/{id} | 图书详情 + 全部借阅记录（一对多，F2） |
| POST | /api/books | 新增图书 |
| POST | /api/books/{id} | 修改图书 |
| POST | /api/books/{id}/delete | 删除图书（逻辑删除，有未归还记录时拒绝） |
| GET | /api/borrow-records/page | 借阅记录分页动态查询（五条件组合，学生仅本人数据） |
| GET | /api/borrow-records/{id} | 借阅记录详情（关联图书/分类信息） |
| POST | /api/borrow-records | 提交阅览申请（学生强制本人，R03 校验） |
| POST | /api/borrow-records/{id} | 修改借阅记录（仅管理员，仅待确认状态） |
| POST | /api/borrow-records/{id}/delete | 删除借阅记录（仅管理员，未归还则恢复库存） |
| POST | /api/borrow-records/{id}/return | 发起归还（本人或管理员） |
| POST | /api/borrow-records/{id}/approve | 确认借阅（仅管理员，扣库存） |
| POST | /api/borrow-records/{id}/reject | 驳回申请（仅管理员） |
| POST | /api/borrow-records/{id}/return-confirm | 确认归还（仅管理员，恢复库存） |
| POST | /api/borrow-records/{id}/return-reject | 驳回归还（仅管理员） |
| GET | /api/comments?bookId= | 图书评论列表 |
| POST | /api/comments | 发表评论 |
| POST | /api/comments/{id}/delete | 删除评论（本人或管理员） |
| GET | /api/notices | 公告列表 |
| GET | /api/notices/latest | 最新一条公告 |
| POST | /api/notices | 发布公告（仅管理员） |
| POST | /api/notices/{id}/delete | 删除公告（仅管理员） |
| GET | /api/search-histories/my | 本人最近 10 条搜索记录 |
| GET | /api/operation-logs | 最近操作日志（仅管理员） |

> **请求方法约定**：本系统只允许 `GET` 与 `POST` 两种方法。所有写操作（新增/修改/状态流转）统一用 `POST`；删除一律为**逻辑删除**（`is_deleted` 置位），对应路径 `POST /{id}/delete`。
> 除 `/api/auth/login` 外，所有接口均需在请求头携带 `Authorization: Bearer <token>`。
