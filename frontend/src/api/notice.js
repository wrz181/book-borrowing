import request from '../utils/request'

/** 查询全部公告 */
export function listNotices() {
  return request.get('/notices')
}

/** 查询最新一条公告 */
export function latestNotice() {
  return request.get('/notices/latest')
}

/** 发布公告（管理员） */
export function createNotice(data) {
  return request.post('/notices', data)
}

/** 删除公告（管理员） */
export function deleteNotice(id) {
  return request.post(`/notices/${id}/delete`)
}
