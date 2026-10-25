import { defineStore } from 'pinia'

const TOKEN_KEY = 'bookborrowing_token'
const USER_KEY = 'bookborrowing_user'

/**
 * 登录用户状态管理：token 与用户信息持久化到 localStorage。
 * 用户信息含 role 字段（admin-管理员 / student-学生），用于页面按钮显隐。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    /** 是否为管理员 */
    isAdmin: (state) => state.userInfo?.role === 'admin'
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.setUser(data.userInfo)
    },
    setUser(userInfo) {
      this.userInfo = userInfo
      localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
