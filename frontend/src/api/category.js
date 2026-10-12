import request from '../utils/request'

/** 分类列表（sortBy: default=创建顺序 name=名称 updateTime=修改时间） */
export function listCategories(sortBy) {
  return request.get('/categories', { params: { sortBy } })
}

/** 新增分类 */
export function createCategory(data) {
  return request.post('/categories', data)
}

/** 更新分类 */
export function updateCategory(id, data) {
  return request.put(`/categories/${id}`, data)
}

/** 删除分类 */
export function deleteCategory(id) {
  return request.delete(`/categories/${id}`)
}
