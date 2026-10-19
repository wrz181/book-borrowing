import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：携带 JWT token
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = 'Bearer ' + userStore.token
    }
    // 当前身份（三员顶栏切换）：后端按此身份解析权限
    if (userStore.activeRoleId) {
      config.headers['X-Active-Role'] = String(userStore.activeRoleId)
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一处理 code / 401 / 业务错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
      return Promise.reject(new Error(res.message || '未登录'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 401：token 失效，跳转登录页
    if (error.response && error.response.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
      return Promise.reject(error)
    }
    // HTTP 403：无权限，展示后端返回的具体提示
    if (error.response && error.response.status === 403) {
      const msg = (error.response.data && error.response.data.message) || '无操作权限'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
