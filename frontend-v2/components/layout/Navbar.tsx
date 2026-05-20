"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { BookOpen, PenLine, BarChart3, Palette, Settings, LogOut } from "lucide-react"
import { cn } from "@/lib/utils"
import { useAuthStore } from "@/stores/auth"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { ThemeToggle } from "@/components/shared/ThemeToggle"

const navItems = [
  { href: "/", label: "首页", icon: BookOpen },
  { href: "/novels", label: "小说", icon: BookOpen },
  { href: "/create", label: "创建", icon: PenLine },
  { href: "/dashboard", label: "面板", icon: BarChart3 },
  { href: "/style-cards", label: "风格卡", icon: Palette },
  { href: "/llm-config", label: "设置", icon: Settings },
]

export function Navbar() {
  const pathname = usePathname()
  const { user, logout } = useAuthStore()

  return (
    <header className="h-12 border-b border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 flex items-center px-5 gap-6 shrink-0">
      <Link href="/" className="flex items-center gap-2">
        <div className="w-7 h-7 rounded-md bg-amber-100 dark:bg-amber-900 flex items-center justify-center">
          <PenLine className="w-4 h-4 text-amber-700 dark:text-amber-400" />
        </div>
        <span className="text-sm font-bold text-stone-900 dark:text-stone-50">MoYun</span>
      </Link>

      <nav className="flex items-center gap-1">
        {navItems.map((item) => {
          const isActive = pathname === item.href || (item.href !== "/" && pathname.startsWith(item.href))
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "px-3 py-1.5 rounded-md text-xs font-medium transition-colors",
                isActive
                  ? "bg-amber-100 dark:bg-amber-900 text-amber-800 dark:text-amber-200"
                  : "text-stone-500 hover:text-stone-900 dark:text-stone-400 dark:hover:text-stone-50 hover:bg-stone-100 dark:hover:bg-stone-700"
              )}
            >
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="flex-1" />

      <ThemeToggle />

      <DropdownMenu>
        <DropdownMenuTrigger className="w-8 h-8 rounded-full p-0 inline-flex items-center justify-center hover:bg-stone-100 dark:hover:bg-stone-700 transition-colors cursor-pointer">
          <Avatar className="w-7 h-7">
            <AvatarFallback className="bg-amber-100 dark:bg-amber-900 text-amber-800 dark:text-amber-200 text-xs font-semibold">
              {user?.nickname?.[0] || user?.username?.[0] || "U"}
            </AvatarFallback>
          </Avatar>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem disabled>{user?.nickname || user?.username}</DropdownMenuItem>
          <DropdownMenuItem onClick={logout}>
            <LogOut className="w-4 h-4 mr-2" />
            退出登录
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}
