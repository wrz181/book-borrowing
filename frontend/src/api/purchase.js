import request from '../utils/request'

/** 分页动态条件查询采购单 */
export function pagePurchases(params) {
  return request.get('/purchases/page', { params })
}

/** 采购单详情 */
export function getPurchase(id) {
  return request.get(`/purchases/${id}`)
}

/** 新增采购单（草稿） */
export function createPurchase(data) {
  return request.post('/purchases', data)
}

/** 修改采购单（仅草稿） */
export function updatePurchase(id, data) {
  return request.post(`/purchases/${id}`, data)
}

/** 删除采购单（草稿/已取消） */
export function deletePurchase(id) {
  return request.post(`/purchases/${id}/delete`)
}

/** 提交申请：草稿(DRAFT) → 提交申请(SUBMIT)，无需再选择应用（超级节点由集成业务配置绑定） */
export function submitApply(id) {
  return request.post(`/purchases/${id}/submit-apply`)
}

/** 审批通过并下发 ERP：提交申请 → 超级节点(SUPER)，body 可空 comment */
export function approveFlow(id, comment) {
  return request.post(`/purchases/${id}/flow/approve`, { comment })
}

/** 驳回/打回：进入已打回(REJECTED)，body 可空 comment */
export function rejectFlow(id, comment) {
  return request.post(`/purchases/${id}/flow/reject`, { comment })
}

/** 打回后重新提交 */
export function resubmitFlow(id) {
  return request.post(`/purchases/${id}/flow/resubmit`)
}

/** 流程详情：data 含 flow（流程实例）、logs（流转日志）、biz（绑定的集成业务配置） */
export function getPurchaseFlow(id) {
  return request.get(`/purchases/${id}/flow`)
}

/** 采购入库：匹配已有图书增加库存，或新增图书 */
export function stockInPurchase(id, remark) {
  return request.post(`/purchases/${id}/stock-in`, null, { params: { remark } })
}

/** 取消采购单 */
export function cancelPurchase(id) {
  return request.post(`/purchases/${id}/cancel`)
}
