"use client"

import { useState, useCallback } from "react"
import { useParams } from "next/navigation"
import Link from "next/link"
import {
  BookOpen,
  Layers,
  Wand2,
  Pencil,
  Download,
  ArrowLeft,
  PanelLeft,
} from "lucide-react"
import useSWR from "swr"
import { StatsCard } from "@/components/shared/StatsCard"
import { EmptyState } from "@/components/shared/EmptyState"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"
import { ChapterList } from "@/components/novel/ChapterList"
import { GenerateDialog } from "@/components/novel/GenerateDialog"
import { RhythmPanel } from "@/components/novel/RhythmPanel"
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
import { chapterApi } from "@/lib/api/chapter"
import { rhythmApi } from "@/lib/api/rhythm"
import { useAuth } from "@/hooks/useAuth"
import { cn } from "@/lib/utils"
import type { NovelVO, ChapterListItem, RhythmStatusVO } from "@/types"

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

export default function NovelDetailPage() {
  const { id } = useParams<{ id: string }>()
  useAuth()

  const { data: novelData, isLoading: novelLoading } = useSWR(
    id ? `novel-${id}` : null,
    () => novelApi.get(id)
  )
  const novel = novelData?.data ?? null

  const { data: chaptersData, isLoading: chaptersLoading, mutate: mutateChapters } = useSWR(
    id ? `chapters-${id}` : null,
    () => chapterApi.list(id)
  )
  const chapters = chaptersData?.data ?? []

  const { data: rhythmData } = useSWR(
    id ? `rhythm-${id}` : null,
    () => rhythmApi.getStatus(id)
  )
  const rhythm = rhythmData?.data ?? null

  const { data: suggestionData } = useSWR(
    id ? `rhythm-suggestion-${id}` : null,
    () => rhythmApi.getSuggestion(id)
  )
  const suggestion = suggestionData?.data ?? undefined

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingChapterNum, setDeletingChapterNum] = useState<number | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [generateOpen, setGenerateOpen] = useState(false)
  const [generateChapterNum, setGenerateChapterNum] = useState(1)

  const handleDeleteChapter = useCallback((chapterNum: number) => {
    setDeletingChapterNum(chapterNum)
    setDeleteOpen(true)
  }, [])

  const confirmDeleteChapter = useCallback(async () => {
    if (deletingChapterNum === null) return
    try {
      await chapterApi.delete(id, deletingChapterNum)
      // Revalidate chapters list
      // mutate(`chapters-${id}`)
    } finally {
      setDeleteOpen(false)
      setDeletingChapterNum(null)
    }
  }, [id, deletingChapterNum])

  const handleGenerate = useCallback(
    (chapterNum: number) => {
      setGenerateChapterNum(chapterNum)
      setGenerateOpen(true)
    },
    []
  )

  const handleExport = useCallback(async () => {
    try {
      const blob = await novelApi.export(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `novel-${id}.txt`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      // export may not be implemented yet
    }
  }, [id])

  if (novelLoading) {
    return (
      <div className="flex h-full">
        <div className="hidden md:block w-[280px] border-r border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-4 space-y-3">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
        <div className="flex-1 p-4 md:p-6 space-y-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      </div>
    )
  }

  if (!novel) {
    return (
      <EmptyState
        icon={<BookOpen className="w-6 h-6 text-stone-400" />}
        title="小说不存在"
        description="未找到该小说，可能已被删除"
        action={
          <Link
            href="/novels"
            className={cn(
              buttonVariants(),
              "bg-amber-600 hover:bg-amber-700 text-white"
            )}
          >
            返回小说列表
          </Link>
        }
      />
    )
  }

  const status = statusMap[novel.status] || statusMap[0]

  const sidebarContent = (
    <>
      {/* Back button */}
      <Link
        href="/novels"
        className="flex items-center gap-1.5 text-xs text-stone-500 hover:text-stone-700 dark:hover:text-stone-300 mb-4"
      >
        <ArrowLeft className="w-3.5 h-3.5" />
        返回列表
      </Link>

      {/* Novel info */}
      <div className="mb-6">
        <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50 mb-2">
          {novel.novelName}
        </h1>
        <div className="flex flex-wrap gap-1.5 mb-3">
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

        {/* Stats */}
        <div className="grid grid-cols-2 gap-2 mb-4">
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

        {/* Action buttons */}
        <div className="flex gap-2">
          <Link
            href={`/novel/${id}/edit`}
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "flex-1"
            )}
          >
            <Pencil className="w-3.5 h-3.5 mr-1" />
            编辑
          </Link>
          <Button variant="outline" size="sm" className="flex-1" onClick={handleExport}>
            <Download className="w-3.5 h-3.5 mr-1" />
            导出
          </Button>
        </div>
      </div>

      {/* Rhythm panel */}
      <RhythmPanel rhythm={rhythm} recommendation={suggestion} />

      {/* Volumes */}
      {novel.volumes && novel.volumes.length > 0 && (
        <div className="mt-6">
          <h2 className="text-xs font-semibold text-stone-900 dark:text-stone-50 mb-2 flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-amber-600" />
            卷目
          </h2>
          <div className="space-y-2">
            {novel.volumes.map((v) => (
              <div
                key={v.arcId}
                className="rounded-lg border border-stone-200 dark:border-stone-700 p-2.5"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-stone-900 dark:text-stone-50">
                    {v.arcName}
                  </span>
                  <span className="text-[10px] text-stone-400">
                    {v.chapterCount}章
                  </span>
                </div>
                {v.arcOutline && (
                  <p className="text-[10px] text-stone-500 mt-1 line-clamp-2">
                    {v.arcOutline}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  )

  return (
    <div className="flex h-full">
      {/* Desktop sidebar */}
      <div className="hidden md:flex w-[280px] border-r border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 p-4 flex-col shrink-0 overflow-y-auto">
        {sidebarContent}
      </div>

      {/* Mobile sidebar sheet */}
      <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
        {/* Mobile info toggle */}
        <div className="md:hidden fixed bottom-4 left-4 z-40">
          <SheetTrigger
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
              "shadow-lg"
            )}
          >
            <PanelLeft className="w-4 h-4 mr-2" />
            查看小说信息
          </SheetTrigger>
        </div>
        <SheetContent side="left" showCloseButton={true}>
          <SheetHeader>
            <SheetTitle>小说信息</SheetTitle>
          </SheetHeader>
          <div className="px-4 pb-4 overflow-y-auto">
            {sidebarContent}
          </div>
        </SheetContent>
      </Sheet>

      {/* Right content: chapter list */}
      <div className="flex-1 overflow-y-auto p-4 md:p-6">
        {chaptersLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : (
          <ChapterList
            novelId={id}
            chapters={chapters}
            onGenerate={handleGenerate}
            onDelete={handleDeleteChapter}
          />
        )}
      </div>

      {/* Delete confirmation dialog */}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除章节"
        description={`确定要删除第${deletingChapterNum}章吗？此操作不可撤销。`}
        onConfirm={confirmDeleteChapter}
        confirmText="删除"
        variant="destructive"
      />

      {/* AI Generate dialog */}
      <GenerateDialog
        open={generateOpen}
        onOpenChange={setGenerateOpen}
        novelId={id}
        defaultChapterNum={generateChapterNum}
        onComplete={() => mutateChapters()}
      />
    </div>
  )
}
