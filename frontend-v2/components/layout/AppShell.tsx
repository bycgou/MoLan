"use client"

import { useAuth } from "@/hooks/useAuth"
import { Navbar } from "./Navbar"
import { LoadingSpinner } from "@/components/shared/LoadingSpinner"

export function AppShell({ children }: { children: React.ReactNode }) {
  const { isLoading, isAuthenticated } = useAuth()

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!isAuthenticated) {
    return null // useAuth will redirect
  }

  return (
    <div className="h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 overflow-hidden">
        {children}
      </main>
    </div>
  )
}
