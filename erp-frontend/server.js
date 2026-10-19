/**
 * 类ERP平台（模拟第三方系统）独立服务
 * ---------------------------------------------------------------
 * 1) 监听独立端口（默认 5174），对外提供一个「另一个本机端口」的类ERP平台页面；
 * 2) 静态托管 public 目录下的页面，无需任何第三方依赖（Node 内置模块即可）；
 * 3) 将 /api/** 反向代理到图书借阅管理系统后端（默认 http://localhost:8080），
 *    使前后端同源，避免跨域问题；
 * 4) 提供 /erp/forms 收单接口：供图书借阅管理系统流程引擎「审批通过」时主动推送采购表单。
 *
 * 启动：node server.js   （或 npm start）
 * 打开：http://localhost:5174/
 *
 * 环境变量：
 *   PORT        本服务端口，默认 5174
 *   BACKEND_URL 图书借阅管理系统后端地址，默认 http://localhost:8080
 */
const http = require('http')
const fs = require('fs')
const path = require('path')

const PORT = Number(process.env.PORT || 5174)
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080'
const PUBLIC_DIR = path.join(__dirname, 'public')

/** 单次收单请求体上限：1MB */
const MAX_BODY_BYTES = 1024 * 1024

/**
 * OA（图书借阅管理系统）推送过来的采购表单内存仓库：重启即清空，演示用。
 * 每条结构：erpFormId(本端自增) / formId(OA 侧 integration_form.id) / 采购与图书字段 / 收单时间
 */
const oaForms = []

/** 本端 ERP 表单号自增序列 */
let erpFormIdSeq = 1000

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon'
}

const server = http.createServer((req, res) => {
  const pathname = (req.url || '').split('?')[0]
  // 撤单：POST /erp/forms/{erpFormId}/cancel（OA 侧流程终止：取消 / 超时打回）
  const cancelMatch = /^\/erp\/forms\/([^/]+)\/cancel$/.exec(pathname)
  if (cancelMatch) {
    handleErpFormCancel(req, res, decodeURIComponent(cancelMatch[1]))
    return
  }
  if (pathname === '/erp/forms') {
    handleErpForms(req, res)
    return
  }
  if (req.url === '/api' || req.url.startsWith('/api/') || req.url.startsWith('/api?')) {
    proxyToBackend(req, res)
    return
  }
  serveStatic(req, res)
})

/**
 * 读取请求体（限制大小，避免被超大报文打爆内存）。
 */
function readBody(req, limitBytes, callback) {
  let size = 0
  const chunks = []
  req.on('data', (chunk) => {
    size += chunk.length
    if (size > limitBytes) {
      req.destroy()
      return
    }
    chunks.push(chunk)
  })
  req.on('end', () => callback(Buffer.concat(chunks).toString('utf8')))
  req.on('error', () => callback(null))
}

/**
 * /erp/forms 收单接口：
 *   POST 接收 OA 推送的采购表单（校验 X-App-Id / X-App-Secret 存在且非空，简化处理），回 {"code":200,"data":{"erpFormId":n}}
 *   GET  返回已收到的表单列表，供页面轮询展示
 */
function handleErpForms(req, res) {
  if (req.method === 'GET') {
    sendJson(res, 200, { code: 200, message: 'success', data: oaForms.slice().reverse() })
    return
  }
  if (req.method !== 'POST') {
    sendJson(res, 405, { code: 405, message: '仅支持 GET/POST' })
    return
  }

  const appId = req.headers['x-app-id']
  const appSecret = req.headers['x-app-secret']
  if (!appId || !String(appId).trim() || !appSecret || !String(appSecret).trim()) {
    sendJson(res, 401, { code: 401, message: '缺少 X-App-Id 或 X-App-Secret 请求头' })
    return
  }

  readBody(req, MAX_BODY_BYTES, (raw) => {
    if (raw === null) {
      sendJson(res, 413, { code: 413, message: '请求体过大或读取失败' })
      return
    }
    let payload
    try {
      payload = JSON.parse(raw)
    } catch (e) {
      sendJson(res, 400, { code: 400, message: '请求体不是合法 JSON' })
      return
    }
    if (!payload || payload.formId === undefined || payload.formId === null) {
      sendJson(res, 400, { code: 400, message: '缺少 formId（OA 侧集成表单ID）' })
      return
    }

    erpFormIdSeq += 1
    const record = {
      erpFormId: erpFormIdSeq,
      formId: payload.formId,
      bizType: payload.bizType,
      purchaseId: payload.purchaseId,
      purchaseNo: payload.purchaseNo,
      bookName: payload.bookName,
      author: payload.author,
      isbn: payload.isbn,
      quantity: payload.quantity,
      unitPrice: payload.unitPrice,
      totalPrice: payload.totalPrice,
      applicantName: payload.applicantName,
      superNodeName: payload.superNodeName,
      appId: String(appId),
      state: 'PENDING',
      cancelReason: '',
      receivedAt: new Date().toISOString()
    }
    oaForms.push(record)
    console.log('[OA 推送] 收到采购表单 ' + (record.purchaseNo || '-') + '（' + (record.bookName || '-') + '），erpFormId=' + record.erpFormId + '，formId=' + record.formId)
    sendJson(res, 200, { code: 200, message: 'success', data: { erpFormId: record.erpFormId } })
  })
}

/**
 * /erp/forms/{erpFormId}/cancel 撤单接口：
 *   POST 由 OA（图书借阅管理系统）在流程终止（取消 / 超时打回）时调用，
 *        把该表单标记为已撤销（state=CANCELED）并记录撤销原因，回 {"code":200,...}；
 *        找不到该单回 {"code":404,...}。
 */
function handleErpFormCancel(req, res, rawErpFormId) {
  if (req.method !== 'POST') {
    sendJson(res, 405, { code: 405, message: '仅支持 POST' })
    return
  }

  const appId = req.headers['x-app-id']
  const appSecret = req.headers['x-app-secret']
  if (!appId || !String(appId).trim() || !appSecret || !String(appSecret).trim()) {
    sendJson(res, 401, { code: 401, message: '缺少 X-App-Id 或 X-App-Secret 请求头' })
    return
  }

  const erpFormId = Number(rawErpFormId)
  const record = Number.isFinite(erpFormId)
    ? oaForms.filter((item) => item.erpFormId === erpFormId)[0]
    : undefined
  if (!record) {
    sendJson(res, 404, { code: 404, message: '未找到 erpFormId=' + rawErpFormId + ' 的推送表单' })
    return
  }

  readBody(req, MAX_BODY_BYTES, (raw) => {
    if (raw === null) {
      sendJson(res, 413, { code: 413, message: '请求体过大或读取失败' })
      return
    }
    let payload = {}
    if (raw && raw.trim()) {
      try {
        payload = JSON.parse(raw)
      } catch (e) {
        sendJson(res, 400, { code: 400, message: '请求体不是合法 JSON' })
        return
      }
    }
    record.state = 'CANCELED'
    record.cancelReason = payload && payload.reason ? String(payload.reason) : ''
    record.updateTime = new Date().toISOString()
    console.log('[OA 撤销] 表单已撤销，erpFormId=' + record.erpFormId + '，formId=' + record.formId
      + '，原因：' + (record.cancelReason || '-'))
    sendJson(res, 200, {
      code: 200,
      message: 'success',
      data: { erpFormId: record.erpFormId, state: record.state, cancelReason: record.cancelReason }
    })
  })
}

/**
 * 反向代理 /api/** 到图书借阅管理系统后端。
 */
function proxyToBackend(req, res) {
  let target
  try {
    target = new URL(req.url, BACKEND_URL)
  } catch (e) {
    sendJson(res, 500, { code: 500, message: '后端地址配置错误：' + BACKEND_URL })
    return
  }

  const headers = Object.assign({}, req.headers, { host: target.host })
  const proxyReq = http.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port || 80,
      path: target.pathname + target.search,
      method: req.method,
      headers
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )

  proxyReq.on('error', (err) => {
    sendJson(res, 502, {
      code: 502,
      message: '无法连接图书借阅管理系统后端（' + BACKEND_URL + '）：' + err.message
        + '。请先启动后端服务（mvn spring-boot:run，端口 8080）。'
    })
  })

  req.pipe(proxyReq)
}

/**
 * 静态文件托管，根路径返回 index.html。
 */
function serveStatic(req, res) {
  const parsed = new URL(req.url, 'http://localhost')
  let pathname = decodeURIComponent(parsed.pathname)
  if (pathname === '/' || pathname === '') {
    pathname = '/index.html'
  }

  const filePath = path.join(PUBLIC_DIR, path.normalize(pathname).replace(/^([/\\])+/, ''))
  if (!filePath.startsWith(PUBLIC_DIR)) {
    sendJson(res, 403, { code: 403, message: '禁止访问' })
    return
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      sendJson(res, 404, { code: 404, message: '资源不存在：' + pathname })
      return
    }
    const ext = path.extname(filePath).toLowerCase()
    res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' })
    res.end(data)
  })
}

/**
 * 发送 JSON 响应。
 */
function sendJson(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload)
  })
  res.end(payload)
}

server.listen(PORT, () => {
  console.log('类ERP平台已启动：http://localhost:' + PORT + '/')
  console.log('后端代理目标：' + BACKEND_URL)
  console.log('请从图书借阅管理系统左侧「集成应用」栏目单点登录进入，或手动粘贴票据登录。')
})
