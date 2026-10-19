import request from '../utils/request'

/** 图书分页动态查询 */
export function pageBooks(params) {
  return request.get('/books/page', { params })
}

/** 图书详情（含全部借阅记录，一对多关联查询） */
export function getBookDetail(id) {
  return request.get(`/books/${id}`)
}

/** 新增图书 */
export function createBook(data) {
  return request.post('/books', data)
}

/** 更新图书 */
export function updateBook(id, data) {
  return request.post(`/books/${id}`, data)
}

/** 删除图书 */
export function deleteBook(id) {
  return request.post(`/books/${id}/delete`)
}
