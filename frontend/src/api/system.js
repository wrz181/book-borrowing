import request from '../utils/request'

/** 可配置日志模块及当前级别 */
export function listLogModules() {
  return request.get('/admin/system/logs/modules')
}

/** 设置模块日志级别 */
export function setLogLevel(module, level) {
  return request.post('/admin/system/logs/level', { module, level })
}

/** 分页查询内存日志 */
export function pageLogs(params) {
  return request.get('/admin/system/logs', { params })
}

/** 清空内存日志 */
export function clearLogs() {
  return request.post('/admin/system/logs/clear')
}

/** 内存监控数据 */
export function getMemoryInfo() {
  return request.get('/admin/system/memory')
}

/** 当前静态资源版本号 */
export function getCacheVersion() {
  return request.get('/admin/system/cache/version')
}

/** 清理 JS 缓存 */
export function clearJsCache() {
  return request.post('/admin/system/cache/clear')
}
