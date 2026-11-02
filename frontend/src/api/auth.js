import request from '../utils/request'

/** 登录 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前用户信息 */
export function getUserInfo() {
  return request.get('/auth/info')
}

/** 退出登录：服务端从 Redis 白名单删除 token，实现会话吊销 */
export function logout() {
  return request.post('/auth/logout')
}
