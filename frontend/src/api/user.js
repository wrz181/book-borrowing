import request from '../utils/request'

/** 账号已绑定的身份角色ID列表（编辑回填用，支持一个账号兼任多角色） */
export function listUserRoleIds(id) {
  return request.post(`/admin/users/${id}/role-ids`)
}
