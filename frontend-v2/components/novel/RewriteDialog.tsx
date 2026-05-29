"use client"

import { useState, useEffect } from "react"
import { RefreshCw } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { chapterApi } from "@/lib/api/chapter"
import { llmConfigApi } from "@/lib/api/llm-config"
import { useSSE } from "@/hooks/useSSE"
import { StreamViewer } from "./StreamViewer"
import type { ChapterVO, LlmConfigVO } from "@/types"

interface RewriteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  novelId: string
  chapter: ChapterVO
  onComplete?: () => void
}

export function RewriteDialog({
  open,
  onOpenChange,
  novelId,
  chapter,
  onComplete,
}: RewriteDialogProps) {
  const [instruction, setInstruction] = useState("")
  const [llmConfigId, setLlmConfigId] = useState("")
  const [configs, setConfigs] = useState<LlmConfigVO[]>([])
  const [showViewer, setShowViewer] = useState(false)

  const streamUrl = chapterApi.streamWriteUrl()
  const { content, isStreaming, error, checkpoints, start, cancel, reset } = useSSE(streamUrl)

  // Load LLM configs on open
  useEffect(() => {
    if (open) {
      llmConfigApi.list().then((res) => setConfigs(res.data ?? [])).catch(console.error)
      setInstruction("")
    }
  }, [open])

  const handleRewrite = () => {
    setShowViewer(true)
    start({
      novelId,
      chapterNum: chapter.chapterNum,
      llmConfigId: llmConfigId || undefined,
      userInstruction: instruction || "请改写这段内容，保持情节但优化文笔",
    })
  }

  const handleSave = async () => {
    try {
      await chapterApi.update(novelId, chapter.chapterNum, {
        content,
      })
      setShowViewer(false)
      onOpenChange(false)
      onComplete?.()
    } catch (e) {
      console.error("Save failed:", e)
    }
  }

  const handleRegenerate = () => {
    reset()
    start({
      novelId,
      chapterNum: chapter.chapterNum,
      llmConfigId: llmConfigId || undefined,
      userInstruction: instruction || "请改写这段内容，保持情节但优化文笔",
    })
  }

  const handleClose = () => {
    cancel()
    setShowViewer(false)
    reset()
    onOpenChange(false)
  }

  return (
    <>
      <Dialog open={open && !showViewer} onOpenChange={handleClose}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <RefreshCw className="w-4 h-4 text-amber-600" />
              AI改写章节
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Original content preview */}
            <div className="space-y-2">
              <Label>原文内容</Label>
              <div className="max-h-[200px] overflow-y-auto rounded-lg border border-stone-200 dark:border-stone-700 p-3 text-sm text-stone-600 dark:text-stone-400 bg-stone-50 dark:bg-stone-800/50">
                {chapter.content.slice(0, 500)}
                {chapter.content.length > 500 && "..."}
              </div>
              <span className="text-xs text-stone-400">
                {chapter.wordCount}字
              </span>
            </div>

            <div className="space-y-2">
              <Label htmlFor="instruction">改写要求</Label>
              <Textarea
                id="instruction"
                placeholder="描述你希望如何改写，例如：增加对话、加强氛围描写、调整节奏..."
                value={instruction}
                onChange={(e) => setInstruction(e.target.value)}
                rows={3}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="llmConfigId">模型配置 (可选)</Label>
              <Select value={llmConfigId} onValueChange={(v) => setLlmConfigId(v ?? "")}>
                <SelectTrigger>
                  <SelectValue placeholder="使用默认配置" />
                </SelectTrigger>
                <SelectContent>
                  {configs.map((cfg) => (
                    <SelectItem key={cfg.configId} value={cfg.configId}>
                      {cfg.configName} ({cfg.modelName})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={handleClose}>
                取消
              </Button>
              <Button
                className="bg-amber-600 hover:bg-amber-700 text-white"
                onClick={handleRewrite}
              >
                <RefreshCw className="w-4 h-4 mr-1" />
                开始改写
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <StreamViewer
        open={showViewer}
        onOpenChange={(v) => {
          if (!v) handleClose()
        }}
        title={`改写: 第${chapter.chapterNum}章 ${chapter.chapterTitle}`}
        content={content}
        isStreaming={isStreaming}
        error={error}
        checkpoints={checkpoints}
        onSave={handleSave}
        onCancel={cancel}
        onRegenerate={!isStreaming ? handleRegenerate : undefined}
      />
    </>
  )
}
