import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getMe } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 1
  },
  actions: {
    async login(form) {
      const data = await apiLogin(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },
    async register(form) {
      await apiRegister(form)
    },
    async refreshMe() {
      if (!this.token) return
      try {
        this.user = await getMe()
        localStorage.setItem('user', JSON.stringify(this.user))
      } catch (e) {
        // token 失效交由拦截器处理
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
