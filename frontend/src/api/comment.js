import request from '../utils/request'

/** 按图书查询评论列表 */
export function listComments(bookId) {
  return request.get('/comments', { params: { bookId } })
}

/** 发表评论 */
export function createComment(data) {
  return request.post('/comments', data)
}

/** 删除评论（本人或管理员） */
export function deleteComment(id) {
  return request.post(`/comments/${id}/delete`)
}
