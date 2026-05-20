import { api } from "@/lib/api"
import type { LoginRequest, RegisterRequest, UserVO } from "@/types"

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<UserVO>("/auth/login", data),

  register: (data: RegisterRequest) =>
    api.post<UserVO>("/auth/register", data),

  getCurrentUser: () =>
    api.get<UserVO>("/auth/me"),
}
