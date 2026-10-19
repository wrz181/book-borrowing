# 类ERP平台（模拟第三方系统）

图书借阅管理系统之外的**独立进程 / 独立端口**应用，用于模拟第三方业务系统（类 ERP），
通过**单点登录票据（SSO Ticket）**与图书借阅管理系统互通。

- 运行端口：**5174**（与图书借阅系统前端 5173、后端 8080 均不同）
- 零第三方依赖：仅使用 Node 内置模块（静态托管 + `/api/**` 反向代理）
- 页面：原生 HTML/CSS/JS 单页应用

## 启动

```powershell
# 前置：图书借阅管理系统后端已启动（默认 http://localhost:8080）
cd erp-frontend
node server.js
# 或 npm start
```

打开 <http://localhost:5174/> 。

可选环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `5174` | 本服务端口 |
| `BACKEND_URL` | `http://localhost:8080` | 图书借阅管理系统后端地址 |

## 单点登录进入方式

1. 用 `groupadmin / 123456` 登录图书借阅管理系统（<http://localhost:5173>）。
2. 左侧「集成应用」栏目点击 **图书采购ERP**，即以票据方式在新窗口打开本平台（`http://localhost:5174/?appId=…&ticket=…`）。
3. 页面向 `POST /api/open/sso/validate` 校验票据，校验通过后展示「登录用户」，实现单点登录打开。

> 也可手动粘贴票据登录：从图书借阅系统的跳转链接中复制 `ticket` 参数，粘贴到登录页输入框。

## 功能

| 功能 | 后端接口 |
| --- | --- |
| 单点登录校验（换取登录用户） | `POST /api/open/sso/validate?ticket=` |
| 待处理表单列表 | `GET /api/open/forms?ticket=&status=0` |
| 已处理表单列表 | `GET /api/open/forms?ticket=&status=1` |
| 办理表单（回传待办 + 消息） | `POST /api/open/forms/{id}/handle` |
| 发起新表单（回传待办 + 消息） | `POST /api/open/form/submit` |

## 与图书借阅系统的完整业务闭环

```text
图书借阅管理系统「图书采购管理」
  新增采购单(草稿) ──提交并下发──▶ 集成表单(status=0) ──▶ 本平台「待处理表单」
                                                              │ 办理（填写处理意见）
                                                              ▼
图书借阅系统：待办中心 +1、消息中心 +1        ◀──回执── 集成表单(status=1)
                                                              │
                                                              ▼
                    采购单「入库」：按 ISBN/名称匹配已有图书增加库存，未匹配则新增图书
```

## 目录

```text
erp-frontend/
├── package.json      # 仅声明启动脚本（无依赖）
├── server.js         # 静态托管 + /api 反向代理（Node 内置模块）
├── public/
│   ├── index.html    # 登录页 + 工作台
│   ├── app.js        # 单点登录校验、表单办理、表单回传
│   └── style.css
└── README.md
```
