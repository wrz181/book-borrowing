import request from '../utils/request'

/** 应用列表 */
export function listApps() {
  return request.get('/admin/apps')
}

/** 新增应用 */
export function createApp(data) {
  return request.post('/admin/apps', data)
}

/** 修改应用 */
export function updateApp(id, data) {
  return request.post(`/admin/apps/${id}`, data)
}

/** 删除应用 */
export function deleteApp(id) {
  return request.post(`/admin/apps/${id}/delete`)
}
