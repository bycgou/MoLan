"use client"

import { useState } from "react"
import { useParams } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, ChevronLeft, ChevronRight, Pencil } from "lucide-react"
import useSWR from "swr"
import { ChapterReader } from "@/components/novel/ChapterReader"
import { RewriteDialog } from "@/components/novel/RewriteDialog"
import { buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { novelApi } from "@/lib/api/novel"
import { chapterApi } from "@/lib/api/chapter"
import { useAuth } from "@/hooks/useAuth"
import { cn } from "@/lib/utils"

export default function ChapterReadPage() {
  const { id, num } = useParams<{ id: string; num: string }>()
  useAuth()

  const chapterNum = Number(num)
  const [rewriteOpen, setRewriteOpen] = useState(false)

  const { data: novelData, isLoading: novelLoading } = useSWR(
    id ? `novel-${id}` : null,
    () => novelApi.get(id)
  )
  const novel = novelData?.data ?? null

  const { data: chapterData, isLoading: chapterLoading, mutate: mutateChapter } = useSWR(
    id && num ? `chapter-${id}-${num}` : null,
    () => chapterApi.get(id, chapterNum)
  )
  const chapter = chapterData?.data ?? null

  const { data: chaptersData } = useSWR(
    id ? `chapters-${id}` : null,
    () => chapterApi.list(id)
  )
  const chapters = chaptersData?.data ?? []
  const totalChapters = chapters.length

  const isLoading = novelLoading || chapterLoading

  if (isLoading) {
    return (
      <div className="flex flex-col h-full">
        <div className="border-b border-stone-200 dark:border-stone-700 px-4 py-2.5 flex items-center gap-3">
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-32" />
        </div>
        <div className="flex-1 flex items-start justify-center p-6">
          <div className="max-w-[680px] w-full space-y-4">
            <Skeleton className="h-6 w-48 mx-auto" />
            <Skeleton className="h-4 w-24 mx-auto" />
            <div className="space-y-3 mt-8">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-5/6" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-4/6" />
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (!chapter) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-stone-500">章节不存在</p>
        <Link
          href={`/novel/${id}`}
          className={cn(
            buttonVariants({ variant: "outline" }),
          )}
        >
          返回小说详情
        </Link>
      </div>
    )
  }

  const hasPrev = chapterNum > 1
  const hasNext = chapterNum < totalChapters

  return (
    <div className="flex flex-col h-full">
      {/* Breadcrumb / toolbar */}
      <div className="border-b border-stone-200 dark:border-stone-700 px-4 py-2.5 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Link
            href={`/novel/${id}`}
            className="flex items-center gap-1 text-xs text-stone-500 hover:text-stone-700 dark:hover:text-stone-300"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            {novel?.novelName ?? "返回"}
          </Link>
          <span className="text-xs text-stone-300 dark:text-stone-600">/</span>
          <span className="text-xs text-stone-700 dark:text-stone-300 font-medium">
            第{chapterNum}章
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href={`/novel/${id}/chapter/${chapterNum}/edit`}
            className={cn(
              buttonVariants({ variant: "outline", size: "sm" }),
            )}
          >
            <Pencil className="w-3.5 h-3.5 mr-1" />
            编辑
          </Link>
        </div>
      </div>

      {/* Reading area */}
      <div className="flex-1 overflow-y-auto p-6">
        <ChapterReader
          chapter={chapter}
          onRewrite={() => setRewriteOpen(true)}
          onVersions={() => {
            /* TODO: open versions dialog */
          }}
          onHookAnalysis={() => {
            /* TODO: open hook analysis dialog */
          }}
        />

        {/* Prev / Next navigation */}
        <div className="max-w-[680px] mx-auto mt-8 pt-6 border-t border-stone-200 dark:border-stone-700 flex items-center justify-between">
          {hasPrev ? (
            <Link
              href={`/novel/${id}/chapter/${chapterNum - 1}`}
              className="flex items-center gap-1 text-xs text-stone-500 hover:text-amber-600"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
              上一章
            </Link>
          ) : (
            <span />
          )}
          {hasNext ? (
            <Link
              href={`/novel/${id}/chapter/${chapterNum + 1}`}
              className="flex items-center gap-1 text-xs text-stone-500 hover:text-amber-600"
            >
              下一章
              <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          ) : (
            <span />
          )}
        </div>
      </div>

      {/* AI Rewrite dialog */}
      {chapter && (
        <RewriteDialog
          open={rewriteOpen}
          onOpenChange={setRewriteOpen}
          novelId={id}
          chapter={chapter}
          onComplete={() => mutateChapter()}
        />
      )}
    </div>
  )
}
