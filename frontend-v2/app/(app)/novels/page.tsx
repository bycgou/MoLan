"use client"

import { useState, useCallback } from "react"
import Link from "next/link"
import {
  BookOpen,
  FileText,
  Layers,
  Wand2,
  Pencil,
  Download,
  List,
} from "lucide-react"
import useSWR from "swr"
import { Sidebar } from "@/components/layout/Sidebar"
import { StatsCard } from "@/components/shared/StatsCard"
import { EmptyState } from "@/components/shared/EmptyState"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"
import { NovelCard } from "@/components/novel/NovelCard"
import { Button, buttonVariants } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Sheet,
  SheetTrigger,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { novelApi } from "@/lib/api/novel"
import { useAuth } from "@/hooks/useAuth"
import { cn } from "@/lib/utils"
import type { NovelVO } from "@/types"

const statusMap: Record<number, { label: string; className: string }> = {
  0: {
    label: "草稿",
    className:
      "bg-stone-100 text-stone-600 dark:bg-stone-700 dark:text-stone-300",
  },
  1: {
    label: "连载中",
    className:
      "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  },
  2: {
    label: "已完结",
    className:
      "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  },
}

export default function NovelsPage() {
  useAuth()
  const { data, isLoading, mutate } = useSWR("novels", () =>
    novelApi.list()
  )
  const novels = data?.data ?? []

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const selected = novels.find((n) => n.novelId === selectedId) ?? null

  const handleDelete = useCallback(
    async (novelId: string) => {
      setDeletingId(novelId)
      setDeleteOpen(true)
    },
    []
  )

  const confirmDelete = useCallback(async () => {
    if (!deletingId) return
    try {
      await novelApi.delete(deletingId)
      if (selectedId === deletingId) setSelectedId(null)
      mutate()
    } finally {
      setDeleteOpen(false)
      setDeletingId(null)
    }
  }, [deletingId, selectedId, mutate])

  const handleExport = useCallback(async (novelId: string) => {
    try {
      const blob = await novelApi.export(novelId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `novel-${novelId}.txt`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      // export may not be implemented yet
    }
  }, [])

  // Build sidebar items from novels list
  const sidebarItems = novels.map((n) => ({
    id: n.novelId,
    label: n.novelName,
    subtitle: `${n.chapterCount}章 / ${(n.wordCount / 10000).toFixed(1)}万字`,
    onClick: () => setSelectedId(n.novelId),
  }))

  if (isLoading) {
    return (
      <div className="flex h-full">
        <div className="hidden md:block w-[220px] border-r border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-4 space-y-3">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
        <div className="flex-1 p-4 md:p-6 space-y-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-32 w-full" />
        </div>
      </div>
    )
  }

  if (novels.length === 0) {
    return (
      <EmptyState
        icon={<BookOpen className="w-6 h-6 text-stone-400" />}
        title="还没有小说"
        description="创建你的第一部小说，开始百万字创作之旅"
        action={
          <Link
            href="/create"
            className={cn(
              buttonVariants(),
              "bg-amber-600 hover:bg-amber-700 text-white"
            )}
          >
            创建小说
          </Link>
        }
      />
    )
  }

  const [sheetOpen, setSheetOpen] = useState(false)

  const sidebarContent = (
    <>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-stone-900 dark:text-stone-50">
          小说列表
        </h2>
        <Link
          href="/create"
          className={cn(
            buttonVariants({ size: "sm" }),
            "h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white"
          )}
        >
          新建
        </Link>
      </div>

      <div className="flex flex-col gap-2 overflow-y-auto flex-1">
        {novels.map((novel) => (
          <div
            key={novel.novelId}
            onClick={() => {
              setSelectedId(novel.novelId)
              setSheetOpen(false)
            }}
          >
            <NovelCard
              novel={novel}
              isActive={novel.novelId === selectedId}
              onDelete={() => handleDelete(novel.novelId)}
              onExport={() => handleExport(novel.novelId)}
            />
          </div>
        ))}
      </div>
    </>
  )

  return (
    <div className="flex h-full">
      {/* Desktop sidebar */}
      <div className="hidden md:flex w-[220px] border-r border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-4 flex-col shrink-0">
        {sidebarContent}
      </div>

      {/* Mobile sidebar sheet */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        <SheetContent side="left" showCloseButton={true}>
          <SheetHeader>
            <SheetTitle>小说列表</SheetTitle>
          </SheetHeader>
          <div className="flex flex-col gap-2 overflow-y-auto flex-1 px-4 pb-4">
            <Link
              href="/create"
              onClick={() => setSheetOpen(false)}
              className={cn(
                buttonVariants({ size: "sm" }),
                "h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white w-fit"
              )}
            >
              新建小说
            </Link>
            {novels.map((novel) => (
              <div
                key={novel.novelId}
                onClick={() => {
                  setSelectedId(novel.novelId)
                  setSheetOpen(false)
                }}
              >
                <NovelCard
                  novel={novel}
                  isActive={novel.novelId === selectedId}
                  onDelete={() => handleDelete(novel.novelId)}
                  onExport={() => handleExport(novel.novelId)}
                />
              </div>
            ))}
          </div>
        </SheetContent>
      </Sheet>

      {/* Right content: selected novel detail */}
      <div className="flex-1 overflow-y-auto">
        {/* Mobile toggle button */}
        {!selected && (
          <div className="md:hidden p-4">
            <SheetTrigger
              className={cn(
                buttonVariants({ variant: "outline", size: "sm" }),
                "w-full"
              )}
            >
              <List className="w-4 h-4 mr-2" />
              查看小说列表
            </SheetTrigger>
          </div>
        )}

        {selected ? (
          <div>
            {/* Mobile back button */}
            <div className="md:hidden p-4 pb-0">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelectedId(null)}
                className="text-stone-500 hover:text-stone-700 dark:hover:text-stone-300 -ml-2"
              >
                <List className="w-4 h-4 mr-1" />
                返回列表
              </Button>
            </div>
            <NovelDetailPanel
              novel={selected}
              onEdit={() => {}}
              onExport={() => handleExport(selected.novelId)}
            />
          </div>
        ) : (
          <div className="hidden md:flex h-full items-center justify-center">
            <EmptyState
              icon={<BookOpen className="w-6 h-6 text-stone-400" />}
              title="选择一部小说"
              description="从左侧列表中选择一部小说查看详情"
            />
          </div>
        )}
      </div>

      {/* Delete confirmation dialog */}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除小说"
        description="确定要删除这部小说吗？此操作不可撤销，所有章节和数据将被永久删除。"
        onConfirm={confirmDelete}
        confirmText="删除"
        variant="destructive"
      />
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Novel detail panel shown on the right side                         */
/* ------------------------------------------------------------------ */

function NovelDetailPanel({
  novel,
  onEdit,
  onExport,
}: {
  novel: NovelVO
  onEdit: () => void
  onExport: () => void
}) {
  const status = statusMap[novel.status] || statusMap[0]

  return (
    <div className="p-6 max-w-3xl">
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50">
            {novel.novelName}
          </h1>
          <div className="flex gap-2 mt-2">
            <Badge variant="secondary" className={status.className}>
              {status.label}
            </Badge>
            {novel.creationMode === "wizard" && (
              <Badge
                variant="secondary"
                className="bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
              >
                <Wand2 className="w-3 h-3 mr-1" />
                向导模式
              </Badge>
            )}
            <span className="text-xs text-stone-400">{novel.genre}</span>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="w-3.5 h-3.5 mr-1" />
            编辑
          </Button>
          <Button variant="outline" size="sm" onClick={onExport}>
            <Download className="w-3.5 h-3.5 mr-1" />
            导出
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        <StatsCard label="章节" value={novel.chapterCount} />
        <StatsCard
          label="字数"
          value={`${(novel.wordCount / 10000).toFixed(1)}万`}
        />
        <StatsCard label="卷数" value={novel.volumes?.length ?? novel.arcCount ?? 0} />
        <StatsCard
          label="模式"
          value={novel.creationMode === "wizard" ? "向导" : "经典"}
        />
      </div>

      {/* Outline */}
      {novel.totalOutline && (
        <div className="mb-6">
          <h2 className="text-sm font-semibold text-stone-900 dark:text-stone-50 mb-2 flex items-center gap-1.5">
            <FileText className="w-4 h-4 text-amber-600" />
            总大纲
          </h2>
          <div className="rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800/50 p-4 text-sm text-stone-600 dark:text-stone-300 whitespace-pre-wrap leading-relaxed">
            {novel.totalOutline}
          </div>
        </div>
      )}

      {/* Volumes */}
      {novel.volumes && novel.volumes.length > 0 && (
        <div className="mb-6">
          <h2 className="text-sm font-semibold text-stone-900 dark:text-stone-50 mb-2 flex items-center gap-1.5">
            <Layers className="w-4 h-4 text-amber-600" />
            卷目
          </h2>
          <div className="space-y-2">
            {novel.volumes.map((v) => (
              <div
                key={v.arcId}
                className="rounded-lg border border-stone-200 dark:border-stone-700 p-3"
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-stone-900 dark:text-stone-50">
                    {v.arcName}
                  </span>
                  <span className="text-[10px] text-stone-400">
                    第{v.startChapter}-{v.endChapter}章 ({v.chapterCount}章)
                  </span>
                </div>
                {v.arcOutline && (
                  <p className="text-xs text-stone-500 mt-1 line-clamp-2">
                    {v.arcOutline}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* World setting */}
      {novel.worldSetting && (
        <div>
          <h2 className="text-sm font-semibold text-stone-900 dark:text-stone-50 mb-2 flex items-center gap-1.5">
            <BookOpen className="w-4 h-4 text-amber-600" />
            世界观设定
          </h2>
          <div className="rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800/50 p-4 text-sm text-stone-600 dark:text-stone-300 whitespace-pre-wrap leading-relaxed">
            {novel.worldSetting}
          </div>
        </div>
      )}
    </div>
  )
}
