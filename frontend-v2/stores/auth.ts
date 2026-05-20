import { create } from "zustand"
import type { UserVO, LoginRequest, RegisterRequest } from "@/types"
import { authApi } from "@/lib/api/auth"

interface AuthState {
  user: UserVO | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (data: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  fetchUser: () => Promise<void>
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isLoading: true,
  isAuthenticated: false,
  login: async (data) => {
    const res = await authApi.login(data)
    localStorage.setItem("token", res.data.token)
    set({ user: res.data, isAuthenticated: true })
  },
  register: async (data) => {
    const res = await authApi.register(data)
    localStorage.setItem("token", res.data.token)
    set({ user: res.data, isAuthenticated: true })
  },
  fetchUser: async () => {
    const token = localStorage.getItem("token")
    if (!token) {
      set({ isLoading: false, isAuthenticated: false })
      return
    }
    try {
      const res = await authApi.getCurrentUser()
      set({ user: res.data, isAuthenticated: true, isLoading: false })
    } catch {
      localStorage.removeItem("token")
      set({ user: null, isAuthenticated: false, isLoading: false })
    }
  },
  logout: () => {
    localStorage.removeItem("token")
    set({ user: null, isAuthenticated: false })
  },
}))
