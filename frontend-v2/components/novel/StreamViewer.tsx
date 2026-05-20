"use client"

import { Loader2 } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"

interface StreamViewerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  content: string
  isStreaming: boolean
  error?: string | null
  onSave?: () => void
  onEdit?: () => void
  onDelete?: () => void
  onRegenerate?: () => void
  onCancel?: () => void
}

export function StreamViewer({
  open,
  onOpenChange,
  title,
  content,
  isStreaming,
  error,
  onSave,
  onEdit,
  onDelete,
  onRegenerate,
  onCancel,
}: StreamViewerProps) {
  const wordCount = content.length

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {title}
            {isStreaming && (
              <Loader2 className="w-4 h-4 animate-spin text-amber-600" />
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto min-h-[200px]">
          <div className="text-reading text-stone-800 dark:text-stone-200 whitespace-pre-wrap leading-relaxed">
            {content || (isStreaming ? "生成中..." : "")}
          </div>
          {error && (
            <div className="text-xs text-red-500 mt-2">{error}</div>
          )}
        </div>

        <div className="flex items-center justify-between pt-4 border-t border-stone-200 dark:border-stone-700">
          <span className="text-xs text-stone-400">{wordCount}字</span>
          <div className="flex gap-2">
            {isStreaming && onCancel && (
              <Button variant="outline" size="sm" onClick={onCancel}>
                取消
              </Button>
            )}
            {!isStreaming && content && onSave && (
              <Button
                size="sm"
                className="bg-amber-600 hover:bg-amber-700 text-white"
                onClick={onSave}
              >
                保存
              </Button>
            )}
            {!isStreaming && content && onEdit && (
              <Button variant="outline" size="sm" onClick={onEdit}>
                编辑
              </Button>
            )}
            {!isStreaming && content && onDelete && (
              <Button
                variant="outline"
                size="sm"
                className="text-red-600"
                onClick={onDelete}
              >
                删除
              </Button>
            )}
            {!isStreaming && onRegenerate && (
              <Button variant="outline" size="sm" onClick={onRegenerate}>
                重新生成
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
