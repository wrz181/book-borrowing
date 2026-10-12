import { defineStore } from 'pinia'

const TOKEN_KEY = 'bookborrowing_token'
const USER_KEY = 'bookborrowing_user'

/**
 * 登录用户状态管理：token 持久化到 localStorage。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  actions: {
    setLogin(data) {
      this.token = data.token
      this.userInfo = data.userInfo
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
