import request from '../utils/request'

/** 待办分页 */
export function pageTodos(params) {
  return request.get('/todos/page', { params })
}

/** 未办待办数 */
export function countPendingTodos() {
  return request.get('/todos/pending-count')
}

/** 办理待办 */
export function doneTodo(id) {
  return request.put(`/todos/${id}/done`)
}
