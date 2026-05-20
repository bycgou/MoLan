"use client"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Recycle } from "lucide-react"
import type { ClueVO } from "@/types"

const typeColors: Record<string, string> = {
  "主线伏笔": "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  "支线伏笔": "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  "暗线": "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200",
}

interface ClueListProps {
  clues: ClueVO[]
  onRecycle?: (clueId: string) => void
}

export function ClueList({ clues, onRecycle }: ClueListProps) {
  if (clues.length === 0) {
    return <div className="text-center py-12 text-stone-400 text-xs">暂无线索</div>
  }

  return (
    <div className="border border-stone-200 dark:border-stone-700 rounded-lg overflow-hidden">
      <div className="grid grid-cols-[1fr_auto_auto_auto_auto] gap-4 px-4 py-2 bg-stone-50 dark:bg-stone-800 text-[10px] font-semibold text-stone-400 uppercase">
        <span>标题</span><span>类型</span><span>埋设章节</span><span>回收章节</span><span>操作</span>
      </div>
      {clues.map((clue) => (
        <div key={clue.clueId} className="grid grid-cols-[1fr_auto_auto_auto_auto] gap-4 px-4 py-2.5 border-t border-stone-100 dark:border-stone-700 items-center">
          <div>
            <div className="text-xs font-medium text-stone-900 dark:text-stone-50">{clue.title}</div>
            <div className="text-[10px] text-stone-400 truncate">{clue.description}</div>
          </div>
          <Badge variant="secondary" className={typeColors[clue.clueType] || ""}>{clue.clueType}</Badge>
          <span className="text-xs text-stone-500">第{clue.plantedChapter}章</span>
          <span className="text-xs text-stone-500">{clue.recycleChapter ? `第${clue.recycleChapter}章` : "-"}</span>
          <div>
            {clue.status === 0 && onRecycle && (
              <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => onRecycle(clue.clueId)}>
                <Recycle className="w-3 h-3 mr-1" />回收
              </Button>
            )}
            {clue.status === 1 && (
              <Badge variant="secondary" className="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200">已回收</Badge>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
