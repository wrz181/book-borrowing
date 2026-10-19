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

/** 归还图书 */
export function returnBorrowRecord(id) {
  return request.post(`/borrow-records/${id}/return`)
}
