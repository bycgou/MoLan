"use client"

import { useEffect } from "react"
import { useRouter } from "next/navigation"
import { useAuthStore } from "@/stores/auth"

export function useAuth(requireAuth = true) {
  const router = useRouter()
  const { user, isLoading, isAuthenticated, fetchUser, logout } = useAuthStore()

  useEffect(() => {
    fetchUser()
  }, [fetchUser])

  useEffect(() => {
    if (!isLoading && requireAuth && !isAuthenticated) {
      router.push("/login")
    }
  }, [isLoading, requireAuth, isAuthenticated, router])

  return { user, isLoading, isAuthenticated, logout }
}
