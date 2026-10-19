import request from '../utils/request'

/** 集成业务配置分页查询（bizType / bizName / enabled / pageNum / pageSize） */
export function pageBizs(params) {
  return request.get('/admin/biz/page', { params })
}

/** 集成业务配置单条详情 */
export function getBiz(id) {
  return request.get(`/admin/biz/${id}`)
}

/** 新增集成业务配置 */
export function createBiz(data) {
  return request.post('/admin/biz', data)
}

/** 修改集成业务配置 */
export function updateBiz(id, data) {
  return request.post(`/admin/biz/${id}`, data)
}

/** 删除集成业务配置（后端逻辑删除） */
export function deleteBiz(id) {
  return request.post(`/admin/biz/${id}/delete`)
}
