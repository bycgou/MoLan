"use client"

import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import type { ChapterVO } from "@/types"

interface ChapterReaderProps {
  chapter: ChapterVO
  onRewrite?: () => void
  onVersions?: () => void
  onHookAnalysis?: () => void
  className?: string
}

export function ChapterReader({
  chapter,
  onRewrite,
  onVersions,
  onHookAnalysis,
  className,
}: ChapterReaderProps) {
  return (
    <div className={cn("max-w-[680px] mx-auto", className)}>
      <div className="text-center mb-6">
        <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50">
          第{chapter.chapterNum}章 {chapter.chapterTitle}
        </h1>
        <div className="text-xs text-stone-400 mt-1">
          {chapter.wordCount}字
          {chapter.updateTime
            ? ` · ${new Date(chapter.updateTime).toLocaleDateString()}`
            : ""}
        </div>
      </div>

      <div className="text-[16px] leading-[2.0] text-stone-800 dark:text-stone-200">
        {chapter.content
          .split("\n")
          .filter((p) => p.trim())
          .map((p, i) => (
            <p key={i} className="mb-3" style={{ textIndent: "2em" }}>
              {p}
            </p>
          ))}
      </div>

      <div className="mt-8 flex justify-center gap-3">
        {onRewrite && (
          <Button variant="outline" size="sm" onClick={onRewrite}>
            AI改写
          </Button>
        )}
        {onVersions && (
          <Button variant="outline" size="sm" onClick={onVersions}>
            版本历史
          </Button>
        )}
        {onHookAnalysis && (
          <Button variant="outline" size="sm" onClick={onHookAnalysis}>
            钩子分析
          </Button>
        )}
      </div>
    </div>
  )
}
