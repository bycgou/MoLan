"use client"

import Link from "next/link"
import { FileText, MoreHorizontal, Trash2, Eye, Edit, Wand2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { ChapterListItem } from "@/types"

const strandColors: Record<string, string> = {
  quest: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  fire: "bg-rose-100 text-rose-800 dark:bg-rose-900 dark:text-rose-200",
  constellation: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
}

const strandLabels: Record<string, string> = {
  quest: "主线",
  fire: "情感",
  constellation: "世界",
}

interface ChapterListProps {
  novelId: string
  chapters: ChapterListItem[]
  onGenerate?: (chapterNum: number) => void
  onDelete?: (chapterNum: number) => void
}

export function ChapterList({ novelId, chapters, onGenerate, onDelete }: ChapterListProps) {
  if (chapters.length === 0) {
    return (
      <div className="text-center py-12 text-stone-400">
        <FileText className="w-8 h-8 mx-auto mb-2 opacity-50" />
        <p className="text-xs">暂无章节</p>
      </div>
    )
  }

  return (
    <div className="border border-stone-200 dark:border-stone-700 rounded-lg overflow-hidden bg-white dark:bg-stone-800">
      <div className="px-4 py-2.5 border-b border-stone-200 dark:border-stone-700 flex items-center justify-between">
        <span className="text-xs font-semibold text-stone-900 dark:text-stone-50">章节列表</span>
        <Button size="sm" className="h-7 text-xs bg-amber-600 hover:bg-amber-700 text-white" onClick={() => onGenerate?.(chapters.length + 1)}>
          <Wand2 className="w-3 h-3 mr-1" />AI生成
        </Button>
      </div>
      {chapters.map((ch) => (
        <div key={ch.chapterId} className="px-4 py-2 border-b border-stone-100 dark:border-stone-700 last:border-0 flex items-center gap-3 hover:bg-stone-50 dark:hover:bg-stone-700/50">
          <span className="text-[10px] text-stone-400 w-5">{ch.chapterNum}</span>
          <Link href={`/novel/${novelId}/chapter/${ch.chapterNum}`} className="flex-1 text-xs font-medium text-stone-900 dark:text-stone-50 hover:text-amber-600 truncate">
            {ch.chapterTitle}
          </Link>
          <Badge variant="secondary" className={strandColors[ch.strandType] || ""}>
            {strandLabels[ch.strandType] || ch.strandType}
          </Badge>
          <span className="text-[10px] text-stone-400">{ch.wordCount}字</span>
          <DropdownMenu>
            <DropdownMenuTrigger className="w-6 h-6 flex items-center justify-center rounded hover:bg-stone-100 dark:hover:bg-stone-700">
              <MoreHorizontal className="w-3 h-3" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>
                <Link href={`/novel/${novelId}/chapter/${ch.chapterNum}`} className="flex items-center"><Eye className="w-4 h-4 mr-2" />阅读</Link>
              </DropdownMenuItem>
              <DropdownMenuItem>
                <Link href={`/novel/${novelId}/chapter/${ch.chapterNum}/edit`} className="flex items-center"><Edit className="w-4 h-4 mr-2" />编辑</Link>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onDelete?.(ch.chapterNum)} className="text-red-600">
                <Trash2 className="w-4 h-4 mr-2" />删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ))}
    </div>
  )
}
