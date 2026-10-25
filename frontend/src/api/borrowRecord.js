import request from '../utils/request'

/** 借阅记录分页动态查询 */
export function pageBorrowRecords(params) {
  return request.get('/borrow-records/page', { params })
}

/** 借阅记录详情 */
export function getBorrowRecord(id) {
  return request.get(`/borrow-records/${id}`)
}

/** 新增借阅记录 */
export function createBorrowRecord(data) {
  return request.post('/borrow-records', data)
}

/** 更新借阅记录 */
export function updateBorrowRecord(id, data) {
  return request.post(`/borrow-records/${id}`, data)
}

/** 删除借阅记录 */
export function deleteBorrowRecord(id) {
  return request.post(`/borrow-records/${id}/delete`)
}

/** 归还图书（学生发起归还申请） */
export function returnBorrowRecord(id) {
  return request.post(`/borrow-records/${id}/return`)
}

/** 系统管理员确认阅览申请 */
export function approveBorrowRecord(id) {
  return request.post(`/borrow-records/${id}/approve`)
}

/** 系统管理员驳回阅览申请 */
export function rejectBorrowRecord(id, reason) {
  return request.post(`/borrow-records/${id}/reject`, { reason })
}

/** 系统管理员确认归还 */
export function confirmReturnBorrowRecord(id) {
  return request.post(`/borrow-records/${id}/return-confirm`)
}

/** 系统管理员驳回归还 */
export function rejectReturnBorrowRecord(id, reason) {
  return request.post(`/borrow-records/${id}/return-reject`, { reason })
}
