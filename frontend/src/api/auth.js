import request from '../utils/request'

/** 登录 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前用户信息 */
export function getUserInfo() {
  return request.get('/auth/info')
}
