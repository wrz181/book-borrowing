import request from '../utils/request'

/** 已启用应用（前台动态栏目） */
export function listSsoApps() {
  return request.get('/sso/apps')
}

/** 生成单点登录 ticket */
export function getSsoTicket(appId) {
  return request.get('/sso/ticket', { params: { appId } })
}
