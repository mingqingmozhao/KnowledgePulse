import { defineStore } from 'pinia'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/api/axios'
import { fetchCurrentUser, login as loginApi, register as registerApi, updateProfile as updateProfileApi } from '@/api/user'
import type { LoginRequest, RegisterRequest, UpdateProfileRequest, User } from '@/types'

interface AuthState {
  accessToken: string
  user: User | null
  bootstrapped: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: getAccessToken(),
    user: null,
    bootstrapped: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    displayName: (state) => state.user?.nickname || state.user?.username || '知识创作者'
  },
  actions: {
    async bootstrap() {
      if (this.bootstrapped) {
        return
      }

      if (!this.accessToken) {
        this.bootstrapped = true
        return
      }

      try {
        this.user = await fetchCurrentUser()
      } catch {
        this.logout()
      } finally {
        this.bootstrapped = true
      }
    },
    setSession(accessToken: string, user: User) {
      this.accessToken = accessToken
      this.user = user
      setAccessToken(accessToken)
    },
    async login(payload: LoginRequest) {
      const response = await loginApi(payload)
      this.setSession(response.accessToken, response.user)
      return response.user
    },
    async register(payload: RegisterRequest) {
      await registerApi(payload)
      return this.login({
        username: payload.username,
        password: payload.password
      })
    },
    async refreshProfile() {
      if (!this.accessToken) {
        return null
      }

      this.user = await fetchCurrentUser()
      return this.user
    },
    async saveProfile(payload: UpdateProfileRequest) {
      this.user = await updateProfileApi(payload)
      return this.user
    },
    logout() {
      this.accessToken = ''
      this.user = null
      this.bootstrapped = true
      clearAccessToken()
    }
  }
})
