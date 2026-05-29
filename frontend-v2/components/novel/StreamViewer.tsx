"use client"

import { Loader2, CheckCircle2, AlertTriangle, XCircle, ArrowRight } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import type { CheckpointEvent } from "@/hooks/useSSE"

const actionConfig: Record<string, { label: string; icon: typeof CheckCircle2; color: string }> = {
  APPROVE: { label: "通过", icon: CheckCircle2, color: "text-green-600" },
  REVISE: { label: "修订", icon: AlertTriangle, color: "text-amber-600" },
  REJECT: { label: "驳回", icon: XCircle, color: "text-red-600" },
  REDIRECT: { label: "重定向", icon: ArrowRight, color: "text-blue-600" },
}

const nodeLabels: Record<string, string> = {
  outline: "章纲审查",
  writing: "正文审查",
  audit_router: "终审决策",
}

interface StreamViewerProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  content: string
  isStreaming: boolean
  error?: string | null
  checkpoints?: CheckpointEvent[]
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
  checkpoints = [],
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

        {/* AuthorAgent 检查点时间线 */}
        {checkpoints.length > 0 && (
          <div className="flex flex-wrap gap-2 px-1 py-2 border-b border-stone-200 dark:border-stone-700">
            {checkpoints.map((cp, i) => {
              const cfg = actionConfig[cp.action] ?? actionConfig.APPROVE
              const Icon = cfg.icon
              return (
                <div
                  key={i}
                  className="flex items-center gap-1.5 text-xs rounded-md px-2 py-1 bg-stone-50 dark:bg-stone-800 border border-stone-200 dark:border-stone-700"
                  title={cp.rationale}
                >
                  <Icon className={`w-3.5 h-3.5 ${cfg.color}`} />
                  <span className="text-stone-500 dark:text-stone-400">
                    {nodeLabels[cp.node] ?? cp.node}
                  </span>
                  <span className={`font-medium ${cfg.color}`}>{cfg.label}</span>
                </div>
              )
            })}
          </div>
        )}

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
