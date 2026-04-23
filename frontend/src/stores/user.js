import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('yn_token') || '')
  const info = ref(JSON.parse(localStorage.getItem('yn_user') || 'null'))

  const isLogin = computed(() => !!token.value)
  const userType = computed(() => info.value?.userType || '')
  const homePath = computed(() => {
    if (userType.value === 'province') return '/province'
    if (userType.value === 'city') return '/city'
    if (userType.value === 'enterprise') return '/enterprise'
    return '/login'
  })

  function setSession(t, u) {
    token.value = t
    info.value = u
    localStorage.setItem('yn_token', t)
    localStorage.setItem('yn_user', JSON.stringify(u))
  }

  async function login(username, password) {
    const r = await authApi.login({ username, password })
    setSession(r.data.token, r.data.user)
    return r.data
  }

  function logout() {
    token.value = ''
    info.value = null
    localStorage.removeItem('yn_token')
    localStorage.removeItem('yn_user')
  }

  return { token, info, isLogin, userType, homePath, login, logout, setSession }
})
