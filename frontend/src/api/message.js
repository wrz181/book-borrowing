import request from '../utils/request'

/** 消息分页 */
export function pageMessages(params) {
  return request.get('/messages/page', { params })
}

/** 未读消息数 */
export function countUnreadMessages() {
  return request.get('/messages/unread-count')
}

/** 标记单条已读 */
export function readMessage(id) {
  return request.put(`/messages/${id}/read`)
}

/** 标记全部已读 */
export function readAllMessages() {
  return request.put('/messages/read-all')
}
