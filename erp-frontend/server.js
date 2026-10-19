/**
 * 类ERP平台（模拟第三方系统）独立服务
 * ---------------------------------------------------------------
 * 1) 监听独立端口（默认 5174），对外提供一个「另一个本机端口」的类ERP平台页面；
 * 2) 静态托管 public 目录下的页面，无需任何第三方依赖（Node 内置模块即可）；
 * 3) 将 /api/** 反向代理到图书借阅管理系统后端（默认 http://localhost:8080），
 *    使前后端同源，避免跨域问题。
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
  if (req.url === '/api' || req.url.startsWith('/api/') || req.url.startsWith('/api?')) {
    proxyToBackend(req, res)
    return
  }
  serveStatic(req, res)
})

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
