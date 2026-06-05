import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO, LoginRequest, RegisterRequest } from '@/types'
import { authApi } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const token = ref<string>(localStorage.getItem('token') || '')
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => user.value?.username || '')
  const role = computed(() => user.value?.role || '')

  async function login(req: LoginRequest) {
    loading.value = true
    try {
      const res = await authApi.login(req)
      if (res.code === 200) {
        user.value = res.data
        token.value = res.data.token
        localStorage.setItem('token', res.data.token)
        // 验证Token有效性
        await fetchUser()
        return { success: true }
      }
      return { success: false, message: res.message }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '登录失败'
      return { success: false, message: msg }
    } finally {
      loading.value = false
    }
  }

  async function register(req: RegisterRequest) {
    loading.value = true
    try {
      const res = await authApi.register(req)
      if (res.code === 200) {
        // 注册成功后自动登录
        user.value = res.data
        token.value = res.data.token
        localStorage.setItem('token', res.data.token)
        return { success: true }
      }
      return { success: false, message: res.message }
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '注册失败'
      return { success: false, message: msg }
    } finally {
      loading.value = false
    }
  }

  // 验证时间缓存（供路由守卫使用）
  const lastVerifiedAt = ref(0)

  async function fetchUser() {
    if (!token.value) return
    try {
      const res = await authApi.getCurrentUser()
      if (res.code === 200) {
        user.value = res.data
        lastVerifiedAt.value = Date.now()
      }
    } catch (err: any) {
      // 仅对 401 清除 token，网络错误保留
      if (err?.response?.status === 401) {
        logout()
        lastVerifiedAt.value = 0
      }
      // 网络错误不清除 token，下次请求会重试
    }
  }

  function resetVerifyCache() {
    lastVerifiedAt.value = 0
  }

  function logout() {
    user.value = null
    token.value = ''
    localStorage.removeItem('token')
  }

  return {
    user,
    token,
    loading,
    isLoggedIn,
    username,
    role,
    lastVerifiedAt,
    login,
    register,
    fetchUser,
    logout,
    resetVerifyCache,
  }
})
