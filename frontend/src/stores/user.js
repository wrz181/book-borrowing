import { defineStore } from 'pinia'

const TOKEN_KEY = 'bookborrowing_token'
const USER_KEY = 'bookborrowing_user'
const PERM_KEY = 'bookborrowing_perms'
const ROLES_KEY = 'bookborrowing_roles'
const ACTIVE_ROLE_KEY = 'bookborrowing_active_role'

/**
 * 登录用户状态管理：token、用户信息、身份角色与权限编码持久化到 localStorage。
 * 支持同账号多身份（三员）顶栏切换：permissions 始终对应 activeRoleId 身份。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
    permissions: JSON.parse(localStorage.getItem(PERM_KEY) || '[]'),
    roles: JSON.parse(localStorage.getItem(ROLES_KEY) || '[]'),
    activeRoleId: JSON.parse(localStorage.getItem(ACTIVE_ROLE_KEY) || 'null')
  }),
  getters: {
    /** 当前用户角色编码（主角色） */
    roleCode: (state) => state.userInfo?.roleCode || '',
    /** 当前生效身份 */
    activeRole: (state) => state.roles.find((r) => r.roleId === state.activeRoleId) || null,
    /** 判断是否拥有某权限 */
    hasPerm: (state) => (code) => state.permissions.includes(code),
    /** 是否拥有任意一个权限 */
    hasAny: (state) => (codes) => codes.some((c) => state.permissions.includes(c))
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = data.userInfo
      this.setPermissions(data.permissions)
      this.setRoles(data.roles || [])
      this.setActiveRoleId(data.activeRoleId ?? data.userInfo?.roleId ?? null)
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
    },
    setUser(userInfo) {
      this.userInfo = userInfo
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
    },
    setPermissions(perms) {
      this.permissions = perms || []
      localStorage.setItem(PERM_KEY, JSON.stringify(this.permissions))
    },
    setRoles(roles) {
      this.roles = roles || []
      localStorage.setItem(ROLES_KEY, JSON.stringify(this.roles))
    },
    setActiveRoleId(roleId) {
      this.activeRoleId = roleId ?? null
      localStorage.setItem(ACTIVE_ROLE_KEY, JSON.stringify(this.activeRoleId))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.roles = []
      this.activeRoleId = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(PERM_KEY)
      localStorage.removeItem(ROLES_KEY)
      localStorage.removeItem(ACTIVE_ROLE_KEY)
    }
  }
})
