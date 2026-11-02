-- =============================================================
-- 图书借阅管理系统 数据库初始化脚本
-- 学生：王睿智    学号：2310160131    班级：计算机(怀)231    项目编号：P01-F2-R03
-- 数据库：MySQL 8.0    字符集：utf8mb4
-- 数据表（8 张，其中 4 张核心业务表 + 4 张辅助功能表）：
--   book_category  图书分类表（主数据，核心）
--   book           图书信息表（多对一关联 book_category，核心）
--   borrow_record  借阅记录表（一对多关联 book，核心）
--   sys_user       系统用户表（JWT 登录使用）
--   book_comment   图书评论表（学生对图书评分留言）
--   sys_notice     系统公告表（管理员发布通知）
--   search_history 搜索历史表（图书搜索自动留痕）
--   operation_log  操作日志表（关键借阅操作审计）
-- 说明：本脚本可直接导入（建库 + 建表 + 测试数据）；
--       可重复执行：开头 DROP 全部表后重建（会清空数据！）。
-- 初始账号：admin / 123456（管理员）、student / 123456（学生 张三）
-- =============================================================

CREATE DATABASE IF NOT EXISTS bookborrowing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE bookborrowing;

-- 全量 DROP（子表在前，可重复执行；执行后数据全部清空）
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS borrow_record;
DROP TABLE IF EXISTS book;
DROP TABLE IF EXISTS book_category;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS book_comment;
DROP TABLE IF EXISTS sys_notice;
DROP TABLE IF EXISTS search_history;
DROP TABLE IF EXISTS operation_log;
SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------
-- 逻辑删除约定（is_deleted）：0-未删除 1-已删除。
-- 所有业务查询均忽略 is_deleted = 1 的记录，删除操作仅标记该字段而不物理删除行。
-- -------------------------------------------------------------

-- -------------------------------------------------------------
-- 1. 图书分类表（主数据）
--    列表默认按创建顺序，页面支持按名称、按修改时间排序。
-- -------------------------------------------------------------
CREATE TABLE book_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    category_name VARCHAR(50)  NOT NULL COMMENT '分类名称',
    category_code VARCHAR(20)  NOT NULL COMMENT '分类编码',
    description   VARCHAR(200) NULL COMMENT '分类描述',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (category_code)
) ENGINE = InnoDB COMMENT = '图书分类表';

-- -------------------------------------------------------------
-- 2. 图书信息表（多对一关联 book_category）
-- -------------------------------------------------------------
CREATE TABLE book (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    book_name   VARCHAR(100)  NOT NULL COMMENT '图书名称',
    author      VARCHAR(50)   NULL COMMENT '作者',
    isbn        VARCHAR(20)   NULL COMMENT 'ISBN编号',
    publisher   VARCHAR(50)   NULL COMMENT '出版社',
    category_id BIGINT        NOT NULL COMMENT '所属分类ID',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
    stock       INT           NOT NULL DEFAULT 0 COMMENT '库存数量',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-在架可借 0-已下架',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_book_category (category_id),
    CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES book_category (id)
) ENGINE = InnoDB COMMENT = '图书信息表';

-- -------------------------------------------------------------
-- 3. 借阅记录表（一对多关联 book：一本图书对应多条借阅记录）
--    状态：0-待确认 1-借阅中 2-已归还 3-已驳回 4-归还待确认
-- -------------------------------------------------------------
CREATE TABLE borrow_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    book_id       BIGINT       NOT NULL COMMENT '借阅图书ID',
    borrower_name VARCHAR(50)  NOT NULL COMMENT '借阅人姓名',
    borrower_no   VARCHAR(30)  NOT NULL COMMENT '借阅人学号/借阅证号',
    borrower_id   BIGINT       NULL COMMENT '借阅人用户ID（关联 sys_user.id，数据范围控制用）',
    borrow_date   DATE         NOT NULL COMMENT '借阅日期',
    due_date      DATE         NOT NULL COMMENT '应还日期',
    return_date   DATE         NULL COMMENT '实际归还日期（未归还为空）',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-待确认 1-借阅中 2-已归还 3-已驳回 4-归还待确认',
    remark        VARCHAR(200) NULL COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_record_book (book_id),
    KEY idx_record_borrower (borrower_no),
    CONSTRAINT fk_record_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB COMMENT = '借阅记录表';

-- -------------------------------------------------------------
-- 4. 系统用户表（JWT 登录使用）
--    密码为 BCrypt 加密；role：admin-管理员 student-学生
-- -------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname    VARCHAR(50)  NULL COMMENT '昵称/姓名',
    role        VARCHAR(20)  NOT NULL DEFAULT 'student' COMMENT '角色：admin-管理员 student-学生',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT = '系统用户表';

-- -------------------------------------------------------------
-- 5. 图书评论表（学生对图书评分留言，多对一关联 book、sys_user）
-- -------------------------------------------------------------
CREATE TABLE book_comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    book_id     BIGINT       NOT NULL COMMENT '图书ID',
    user_id     BIGINT       NOT NULL COMMENT '评论用户ID',
    user_name   VARCHAR(50)  NOT NULL COMMENT '评论人姓名（冗余展示用）',
    rating      TINYINT      NOT NULL DEFAULT 5 COMMENT '评分：1-5 星',
    content     VARCHAR(500) NOT NULL COMMENT '评论内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_comment_book (book_id),
    KEY idx_comment_user (user_id),
    CONSTRAINT fk_comment_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '图书评论表';

-- -------------------------------------------------------------
-- 6. 系统公告表（管理员发布、全体用户可见）
-- -------------------------------------------------------------
CREATE TABLE sys_notice (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    title         VARCHAR(100) NOT NULL COMMENT '公告标题',
    content       VARCHAR(1000) NOT NULL COMMENT '公告内容',
    publisher_name VARCHAR(50) NULL COMMENT '发布人姓名',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT = '系统公告表';

-- -------------------------------------------------------------
-- 7. 搜索历史表（图书搜索时自动记录，用于留痕与个人检索回顾）
-- -------------------------------------------------------------
CREATE TABLE search_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '搜索用户ID',
    keyword     VARCHAR(100) NOT NULL COMMENT '搜索关键字',
    search_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_sh_user (user_id),
    CONSTRAINT fk_sh_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '搜索历史表';

-- -------------------------------------------------------------
-- 8. 操作日志表（借阅申请/审批/归还等关键操作自动留痕，审计用）
-- -------------------------------------------------------------
CREATE TABLE operation_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '操作用户ID',
    user_name   VARCHAR(50)  NULL COMMENT '操作人姓名（冗余展示用）',
    action      VARCHAR(30)  NOT NULL COMMENT '操作类型：提交申请/确认借阅/驳回/申请归还/确认归还/驳回归还',
    detail      VARCHAR(500) NULL COMMENT '操作详情',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_ol_user (user_id),
    CONSTRAINT fk_ol_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '操作日志表';

-- -------------------------------------------------------------
-- 测试数据
-- -------------------------------------------------------------
INSERT INTO sys_user (username, password, nickname, role) VALUES
('admin',   '$2b$10$iwzCB6vYghCfwpHpuHyOiupBsX1O0QX3ba8/f7X0DnhsQdYpIKMxq', '系统管理员', 'admin'),
('student', '$2b$10$iwzCB6vYghCfwpHpuHyOiupBsX1O0QX3ba8/f7X0DnhsQdYpIKMxq', '张三',       'student');

INSERT INTO book_category (category_name, category_code, description) VALUES
('文学',   'T1001', '小说、散文、诗歌等文学类图书'),
('计算机', 'T1002', '编程语言、框架、数据库等计算机类图书'),
('历史',   'T1003', '中国史、世界史、历史人物传记'),
('经济管理', 'T1004', '经济学、管理学、金融投资类图书');

INSERT INTO book (book_name, author, isbn, publisher, category_id, price, stock, status) VALUES
('红楼梦',               '曹雪芹',       '9787020002207', '人民文学出版社',   1,  39.50, 5, 1),
('活着',                 '余华',         '9787506365437', '作家出版社',       1,  20.00, 4, 1),
('人类简史',             '尤瓦尔·赫拉利', '9787508647357', '中信出版社',       3,  68.00, 2, 1),
('Java编程思想（第4版）', 'Bruce Eckel',  '9787111213826', '机械工业出版社',   2, 108.00, 3, 1),
('深入理解Java虚拟机（第3版）', '周志明', '9787111661847', '机械工业出版社',   2, 129.00, 2, 1),
('Spring实战（第5版）',   'Craig Walls',  '9787115547179', '人民邮电出版社',   2,  89.00, 3, 1),
('中国历代政治得失',      '钱穆',         '9787108034582', '生活·读书·新知三联书店', 3, 28.00, 2, 1),
('经济学原理（第8版）',   '曼昆',         '9787301313481', '北京大学出版社',   4,  98.00, 2, 1),
('资本论（第一卷）',      '马克思',       '9787010180035', '人民出版社',       4,  58.00, 1, 0);

-- 借阅记录覆盖各状态，便于演示审批流与 F2 一对多查询（图书 1、4 各有两条记录）
INSERT INTO borrow_record (book_id, borrower_name, borrower_no, borrower_id, borrow_date, due_date, return_date, status, remark) VALUES
(1, '李娜', '2021002', NULL, '2026-09-20', '2026-10-20', NULL,        1, NULL),
(4, '李娜', '2021002', NULL, '2026-09-25', '2026-10-25', NULL,        1, NULL),
(2, '张三', '2021005', 2,    '2026-10-08', '2026-11-07', NULL,        0, '学生账号提交的阅览申请，等待管理员确认'),
(3, '张三', '2021005', 2,    '2026-09-01', '2026-10-01', '2026-09-15', 2, '按期归还'),
(5, '张三', '2021005', 2,    '2026-10-12', '2026-11-11', NULL,        4, '已申请归还，等待管理员确认'),
(1, '张伟', '2021001', NULL, '2026-09-01', '2026-10-01', '2026-09-15', 2, '按期归还'),
(4, '王强', '2021003', NULL, '2026-10-01', '2026-10-31', NULL,        1, '续借一次');

INSERT INTO sys_notice (title, content, publisher_name) VALUES
('图书馆开放时间调整通知', '自本月起，图书馆开放时间调整为每日 8:00-21:30，法定节假日闭馆，请各位读者合理安排借阅时间。', '系统管理员'),
('10月新书上架通知', '本月新到计算机、文学类图书共 36 种，已全部上架，欢迎同学们检索借阅。', '系统管理员');

INSERT INTO book_comment (book_id, user_id, user_name, rating, content) VALUES
(1, 2, '张三', 5, '红楼梦百读不厌，人物刻画入木三分，强烈推荐。'),
(4, 2, '张三', 4, 'Java 入门经典，例子很扎实，就是篇幅偏长。'),
(6, 2, '张三', 5, 'Spring 实战讲解循序渐进，配合项目实践效果很好。');
