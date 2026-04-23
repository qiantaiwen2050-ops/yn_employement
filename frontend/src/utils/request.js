import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (resp) => {
    const r = resp.data
    if (r && typeof r.code === 'number') {
      if (r.code === 0) return r
      ElMessage.error(r.message || '请求失败')
      return Promise.reject(r)
    }
    return resp
  },
  (err) => {
    const status = err.response?.status
    const r = err.response?.data
    const msg = r?.message || err.message || '网络异常'
    if (status === 401) {
      ElMessage.warning('登录已失效，请重新登录')
      const userStore = useUserStore()
      userStore.logout()
      router.replace('/login')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request
