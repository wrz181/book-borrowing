import request from '../utils/request'

/** 账号分页查询 */
export function pageUsers(params) {
  return request.get('/admin/users/page', { params })
}

/** 新增账号 */
export function createUser(data) {
  return request.post('/admin/users', data)
}

/** 修改账号 */
export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

/** 删除账号 */
export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

/** 重置密码 */
export function resetUserPassword(id, password) {
  return request.put(`/admin/users/${id}/password`, { password })
}

/** 角色列表（含权限编码） */
export function listRoles() {
  return request.get('/admin/roles')
}

/** 全部权限点 */
export function listPermissions() {
  return request.get('/admin/roles/permissions')
}

/** 更新角色权限 */
export function updateRolePermissions(id, permCodes) {
  return request.put(`/admin/roles/${id}/permissions`, { permCodes })
}
