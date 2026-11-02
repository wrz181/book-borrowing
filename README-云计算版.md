# 图书借阅管理系统（云计算大作业版）

> 在 J2EE 图书借阅系统（Spring Boot 3.2.5 + MyBatis + Vue 3 + MySQL）基础上，按《云计算基础》大作业要求
> 叠加 **Redis + 分布式（HDFS）+ 云原生部署** 三大技术改造。
> 大作业题目方向：基于 redis+分布式+云计算技术的项目实现。

## 〇、大作业要求速查（完整版，来自《【云计算】大作业模版.doc》）

> ⚠️ 写报告时仍需打开模板 .doc 按其原格式撰写（红色要求文字完稿后删除），本节仅作速查防遗漏。

**交付物（三样）**

| 交付物 | 硬性要求 |
|--------|----------|
| 📄 报告 | 按模板五部分撰写，删除红色要求文字；电子稿先交**张老师**初检，纸质版交**卢老师**终审 |
| 🎬 视频 | 长度 **≤ 8 分钟**，大小 **≤ 500MB** |
| 💾 源码 | 与视频配套的可运行工程 |

**报告五部分结构与字数下限**

| 部分 | 内容与要求 |
|------|-----------|
| 一、项目概况 | 1. 项目背景 300-700 字（为什么做/社会价值/对掌握云计算的意义）；2. 功能描述 500-1000 字（可含功能模块图） |
| 二、技术方案 | 1. 方案描述 100-500 字（选型与考虑）；2. 某技术功能 500-1000 字（主要技术在项目中的作用） |
| 三、实现流程 | 按实际步骤分步写，**步骤 ≥ 3 个**，800-1500 字；可贴核心代码（≤2 页，不计字数） |
| 四、结果展示 | 功能截图 **≥ 3 张**，每张配详细文字描述 |
| 五、心得体会 | 1. 技术体系评估 200-500 字；2. 课程技术收获 200-500 字；3. 学习过程感悟 200-500 字 |

**数量红线**

- 数据表 **≥ 8 张**（本项目 8 张 ✅）
- 主页面 **≥ 5 个**（本项目 6 个 ✅）
- 功能截图 **≥ 3 张**，实现流程步骤 **≥ 3 个**

**文件命名**

```
【云计算基础】大作业-专业-班级-学号-姓名
→ 【云计算基础】大作业-计算机231-2310160131-王睿智
```

## 一、本次改造内容（报告"技术方案"素材对照）

| 技术点 | 实现内容 | 核心代码位置 |
|--------|----------|--------------|
| **Redis 热门排行** | ZSet 实时热度统计：浏览详情 +1 分、借阅申请 +10 分；新接口 `GET /api/books/hot` | `HotBookServiceImpl`、`BookController.hot` |
| **Redis 缓存** | 图书详情 cache-aside 缓存（5 分钟 TTL），修改/删除图书自动失效 | `BookServiceImpl.getDetail`、`RedisSupportService` |
| **Redis 分布式会话** | 登录 token 写入 Redis 白名单（TTL 同 JWT），拦截器二次校验，登出即服务端吊销，活跃自动续期 | `AuthController`、`JwtInterceptor` |
| **HDFS 分布式归档** | 一键把全部借阅记录导出 CSV 归档到 HDFS 集群，支持文件列表与内容预览 | `HdfsServiceImpl`、`HdfsController` |
| **高可用降级设计** | Redis 不可用→排行自动降级为数据库统计、缓存/会话自动失效；30 秒熔断避免拖慢请求；HDFS 关闭→接口友好提示 | `RedisSupportService`（降级与熔断） |
| **云原生部署** | 后端/前端 Dockerfile + docker-compose 一键编排（MySQL/Redis/Hadoop/后端/前端），配置全部环境变量化 | `Dockerfile`、`docker-compose.yml` |

数据库仍为原来的 **8 张表**（book、book_category、borrow_record、sys_user、book_comment、sys_notice、search_history、operation_log），无表结构变更，原有 `bookborrowing.sql` 直接可用。

页面共 **7 个**：登录页、首页看板（新增）、图书信息管理、图书详情与借阅记录、图书分类管理、借阅记录管理、HDFS 数据归档（管理员，新增）。

## 〇二、2026-11 更新（本轮完善）

| 变更 | 说明 |
|------|------|
| **前端新增「HDFS 数据归档」页** | 管理员可在页面上一键归档、查看归档文件列表与 CSV 预览（`views/hdfs/HdfsArchive.vue` + `api/hdfs.js`），无需再用 curl 演示 |
| **借阅链路缓存一致性修复** | 借阅申请/审批/归还/删除等操作现在会同步失效 `bookborrowing:cache:book:detail:{id}` 详情缓存（`RedisSupportService.evictBookDetailCache`），消除 5 分钟内的脏读窗口 |
| **缓存反序列化往返修复（重要）** | Jackson 时间格式此前只配了序列化方向，缓存命中反序列化 `yyyy-MM-dd HH:mm:ss` 必然失败；现成对注册反序列化器（`JacksonConfig`），且缓存读写的序列化异常不再误触 30 秒熔断（按未命中处理，只对连接/命令异常熔断） |
| **Redis 连接工厂加固** | 关闭 Lettuce 共享原生连接（`RedisConfig`），每次操作独立短连接，坏连接只影响单次操作，可用性探测始终真实，避免共享连接损坏后的持续假降级 |
| **Redis 可用性探测修复** | 修正 spring-data-redis 3.x 中 `ping()` 返回 String 导致的编译错误 |
| **HDFS 连接编译修复** | `FileSystem.get(..., user)` 抛出的 InterruptedException 现已正确处理（转 IOException 并恢复中断标记） |
| **docker-compose 后端端口映射** | backend 服务新增 `8080:8080`，方便容器模式下直接调用 `/api/*` 接口演示 |

## 二、本机开发运行（不用 Docker）

### 0. 前置环境

- JDK 17+（本机已装 `E:\java\jdk-21.0.11`，IDEA 里选它即可）
- MySQL 8.0、Node.js
- **Redis for Windows（三选一，均免费）**：
  1. 推荐 [Memurai](https://www.memurai.com/get-memurai-windows/)（Redis 兼容，装完即 Windows 服务，端口 6379）；
  2. WSL2 里 `sudo apt install redis-server` 后 `redis-server` 启动；
  3. 有 Docker Desktop 时 `docker run -d --name redis -p 6379:6379 redis:7-alpine`。

### 1. 数据库

执行项目根目录 `bookborrowing.sql`（建库 + 8 张表 + 测试数据）。连接信息在
`src/main/resources/application.yml` 的 `spring.datasource.password`，改成你本机 MySQL 的 root 密码。

### 2. 启动后端

```bash
# IDEA 直接运行 BookBorrowingApplication，或命令行：
set JAVA_HOME=E:\java\jdk-21.0.11
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
```

### 4. 验证新功能

1. 登录后进入「首页看板」→ 初始可能显示"数据库降级"或空；
2. 多点几本图书的「详情」、提交几次借阅申请 → 回看板点「刷新」→ 排名与热度分（Redis 实时）出现；
3. 打开终端执行 `redis-cli`：
   - `ZREVRANGE bookborrowing:rank:hot 0 -1 WITHSCORES` 能看到热度分；
   - `KEYS bookborrowing:cache:book:detail:*` 能看到详情缓存；
4. 退出登录后再用旧 token 调接口 → 401（服务端已吊销）。

> 演示"降级"效果：把 Redis 服务停掉重启后端（或把 `app.redis.enabled` 改 false），看板会自动变成「数据库降级」标签，系统一切功能照常——这是报告里"高可用设计"的好素材。

## 三、对接 Hadoop HDFS（两种方式任选）

### 方式 A：对接实训手册搭的三节点集群（推荐，视频里更贴合课程）

按《基于 CentOS7 的 Hadoop 三节点集群搭建实训手册》把 hadoop101/102/103 搭好后，改 `application.yml`：

```yaml
app:
  hdfs:
    enabled: true
    name-node: hdfs://hadoop101:9000   # core-site.xml 里 fs.defaultFS 的值
    user: root
```

重启后端，用 admin 登录 →「首页看板」之外新增的接口（也可直接用 Apifox/curl 调）：

```bash
# 归档：把全部借阅记录导出 CSV 写入 HDFS
curl -X POST http://localhost:8080/api/hdfs/archive/run -H "Authorization: Bearer <token>"
# 归档文件列表
curl http://localhost:8080/api/hdfs/archive/files -H "Authorization: Bearer <token>"
# 预览文件前 20 行
curl "http://localhost:8080/api/hdfs/archive/preview?fileName=xxx.csv" -H "Authorization: Bearer <token>"
```

同时可在 NameNode Web UI（http://hadoop101:9870）里 Browse Files 看到 `/bookborrowing/archive/` 下的归档文件——录视频时这一幕很直观。

### 方式 B：Docker Compose 一键起全套（含单机 Hadoop）

```bash
docker compose --profile hadoop up -d
# 前端 http://localhost:8081   HDFS Web UI http://localhost:9870
```

compose 已自动把 `APP_HDFS_ENABLED=true`、`APP_HDFS_NAME_NODE=hdfs://hdfs-nn:9000` 传给后端。

## 四、演示视频脚本建议（≤8 分钟，可用 OBS 免费录制）

| 时间 | 内容 |
|------|------|
| 0:00–1:00 | 项目定位与总体架构图：Vue3 前端 → Spring Boot 后端（MySQL / Redis / HDFS / Hadoop 集群），点出"Redis+分布式+云计算"三个关键词 |
| 1:00–2:00 | 原有业务走查：登录 → 图书列表 → 借阅申请 → 管理员审批（展示 8 张表、6 个页面规模） |
| 2:00–3:30 | **Redis 热门排行**：浏览+借阅几本书 → 看板刷新出排名 → redis-cli 展示 ZSET 数据与分数 |
| 3:30–4:30 | **Redis 缓存与会话**：第二次进详情明显走缓存（日志）；登出后旧 token 立即 401 |
| 4:30–6:00 | **HDFS 分布式归档**：执行归档 → 接口返回文件路径 → NameNode Web UI 里看到 /bookborrowing/archive 文件 → 预览 CSV 内容 |
| 6:00–7:00 | **高可用降级**：停掉 Redis 重启后端 → 系统照常运行、排行切换"数据库降级"标签 |
| 7:00–8:00 | Docker Compose 容器化部署展示（`docker compose ps` 列出 5 个容器）+ 总结 |

## 五、提交清单

- 报告：按《【云计算】大作业模版.doc》五部分撰写，完成后删除红色要求文字；
  文件命名 **【云计算基础】大作业-计算机231-2310160131-王睿智**
- 视频：≤8 分钟、≤500MB（OBS 录制 mp4，分辨率 1080p 时长 8 分钟约 200–400MB）
- 源码：本项目整体打包 zip（含 `README-云计算版.md`、`docker-compose.yml`、`bookborrowing.sql`；`.git` 目录可删）

> 注意：启用 Redis 会话校验后，之前登录的旧 token 会提示重新登录一次，属正常现象。
