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
  return request.put(`/purchases/${id}`, data)
}

/** 删除采购单（草稿/已取消） */
export function deletePurchase(id) {
  return request.delete(`/purchases/${id}`)
}

/** 提交采购单：可同时下发到指定集成应用 */
export function submitPurchase(id, appId) {
  return request.put(`/purchases/${id}/submit`, null, { params: { appId } })
}

/** 采购入库：匹配已有图书增加库存，或新增图书 */
export function stockInPurchase(id, remark) {
  return request.put(`/purchases/${id}/stock-in`, null, { params: { remark } })
}

/** 取消采购单 */
export function cancelPurchase(id) {
  return request.put(`/purchases/${id}/cancel`)
}
