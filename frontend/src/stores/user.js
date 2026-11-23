import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi, getMe } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isLogin: (state) => Boolean(state.token && state.user),
    isAdmin: (state) => state.user?.role === 'ADMIN',
    displayName: (state) => state.user?.nickname || state.user?.username || '',
  },
  actions: {
    setAuth({ token, user }) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
    },
    async login(payload) {
      const data = await loginApi(payload)
      this.setAuth(data)
      return data.user
    },
    async register(payload) {
      const data = await registerApi(payload)
      this.setAuth(data)
      return data.user
    },
    async refreshMe() {
      try {
        const user = await getMe()
        this.user = user
        localStorage.setItem('user', JSON.stringify(user))
      } catch {
        // 401 已由拦截器统一处理
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})
