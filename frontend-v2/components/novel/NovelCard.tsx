import Link from "next/link"
import { MoreHorizontal, Edit, Trash2, Download } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { NovelVO } from "@/types"

const statusMap: Record<number, { label: string; className: string }> = {
  0: { label: "草稿", className: "bg-stone-100 text-stone-600 dark:bg-stone-700 dark:text-stone-300" },
  1: { label: "连载中", className: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200" },
  2: { label: "已完结", className: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200" },
}

interface NovelCardProps {
  novel: NovelVO
  isActive?: boolean
  onDelete?: () => void
  onExport?: () => void
}

export function NovelCard({ novel, isActive, onDelete, onExport }: NovelCardProps) {
  const status = statusMap[novel.status] || statusMap[0]

  return (
    <Card
      className={`border-stone-200 dark:border-stone-700 hover:shadow-md transition-all cursor-pointer ${isActive ? "ring-2 ring-amber-500" : ""}`}
    >
      <CardContent className="p-3">
        <div className="flex items-start justify-between">
          <Link href={`/novel/${novel.novelId}`} className="flex-1 min-w-0">
            <div className="text-sm font-semibold text-stone-900 dark:text-stone-50 truncate">
              {novel.novelName}
            </div>
            <div className="text-xs text-stone-400 mt-0.5">{novel.genre}</div>
          </Link>
          <DropdownMenu>
            <DropdownMenuTrigger className="w-6 h-6 shrink-0 flex items-center justify-center rounded hover:bg-stone-100 dark:hover:bg-stone-700 cursor-pointer">
              <MoreHorizontal className="w-4 h-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>
                <Link
                  href={`/novel/${novel.novelId}/edit`}
                  className="flex items-center"
                >
                  <Edit className="w-4 h-4 mr-2" />
                  编辑
                </Link>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onExport}>
                <Download className="w-4 h-4 mr-2" />
                导出
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={onDelete}
                variant="destructive"
              >
                <Trash2 className="w-4 h-4 mr-2" />
                删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <div className="flex gap-1.5 mt-2">
          <Badge variant="secondary" className={status.className}>
            {status.label}
          </Badge>
          {novel.creationMode === "wizard" && (
            <Badge
              variant="secondary"
              className="bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
            >
              向导模式
            </Badge>
          )}
        </div>
        <div className="flex gap-4 mt-2 text-[10px] text-stone-400">
          <span>{novel.chapterCount}章</span>
          <span>{(novel.wordCount / 10000).toFixed(1)}万字</span>
        </div>
      </CardContent>
    </Card>
  )
}
