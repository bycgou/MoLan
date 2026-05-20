"use client"

import Link from "next/link"
import { cn } from "@/lib/utils"
import { buttonVariants } from "@/components/ui/button"
import { Plus } from "lucide-react"

interface SidebarItem {
  id: string
  label: string
  subtitle?: string
  href?: string
  onClick?: () => void
}

interface SidebarProps {
  title: string
  items: SidebarItem[]
  activeId?: string
  createAction?: { label: string; href?: string; onClick?: () => void }
  className?: string
}

export function Sidebar({ title, items, activeId, createAction, className }: SidebarProps) {
  return (
    <aside
      className={cn(
        "w-[220px] border-r border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-4 flex flex-col shrink-0",
        className
      )}
    >
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-stone-900 dark:text-stone-50">
          {title}
        </h2>
        {createAction &&
          (createAction.href ? (
            <Link
              href={createAction.href}
              className={cn(
                buttonVariants({ size: "sm" }),
                "h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white"
              )}
            >
              <Plus className="w-3 h-3 mr-1" />
              {createAction.label}
            </Link>
          ) : (
            <button
              onClick={createAction.onClick}
              className={cn(
                buttonVariants({ size: "sm" }),
                "h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white"
              )}
            >
              <Plus className="w-3 h-3 mr-1" />
              {createAction.label}
            </button>
          ))}
      </div>

      <div className="flex flex-col gap-1.5 overflow-y-auto flex-1">
        {items.map((item) => {
          const isActive = item.id === activeId
          const content = (
            <div
              className={cn(
                "px-3 py-2.5 rounded-lg transition-colors cursor-pointer",
                isActive
                  ? "bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800"
                  : "border border-transparent hover:bg-stone-50 dark:hover:bg-stone-700"
              )}
            >
              <div
                className={cn(
                  "text-xs font-medium",
                  isActive
                    ? "text-stone-900 dark:text-stone-50"
                    : "text-stone-600 dark:text-stone-300"
                )}
              >
                {item.label}
              </div>
              {item.subtitle && (
                <div className="text-[10px] text-stone-400 mt-0.5">
                  {item.subtitle}
                </div>
              )}
            </div>
          )

          return item.href ? (
            <Link key={item.id} href={item.href}>
              {content}
            </Link>
          ) : (
            <div key={item.id} onClick={item.onClick}>
              {content}
            </div>
          )
        })}
      </div>
    </aside>
  )
}
