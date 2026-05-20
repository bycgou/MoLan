"use client"

import { useState, useEffect } from "react"
import { Wand2 } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
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
import type { LlmConfigVO } from "@/types"

const strandOptions = [
  { value: "quest", label: "主线" },
  { value: "fire", label: "情感" },
  { value: "constellation", label: "世界" },
]

interface GenerateDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  novelId: string
  defaultChapterNum?: number
  onComplete?: () => void
}

export function GenerateDialog({
  open,
  onOpenChange,
  novelId,
  defaultChapterNum = 1,
  onComplete,
}: GenerateDialogProps) {
  const [chapterNum, setChapterNum] = useState(defaultChapterNum)
  const [chapterTitle, setChapterTitle] = useState("")
  const [strandType, setStrandType] = useState("quest")
  const [configId, setConfigId] = useState("")
  const [configs, setConfigs] = useState<LlmConfigVO[]>([])
  const [showViewer, setShowViewer] = useState(false)

  const streamUrl = `/api/novel/${novelId}/chapter/stream-write`
  const { content, isStreaming, error, start, cancel, reset } = useSSE(streamUrl)

  // Load LLM configs on open
  useEffect(() => {
    if (open) {
      llmConfigApi.list().then((res) => setConfigs(res.data ?? [])).catch(console.error)
      setChapterNum(defaultChapterNum)
    }
  }, [open, defaultChapterNum])

  const handleGenerate = () => {
    if (!chapterTitle.trim()) return

    setShowViewer(true)
    start({
      novelId,
      chapterNum,
      chapterTitle,
      strandType,
      configId: configId || undefined,
    })
  }

  const handleSave = async () => {
    try {
      await chapterApi.saveManual(novelId, {
        chapterNum,
        chapterTitle,
        content,
        summaryData: null,
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
      chapterNum,
      chapterTitle,
      strandType,
      configId: configId || undefined,
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
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Wand2 className="w-4 h-4 text-amber-600" />
              AI生成章节
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="chapterNum">章节序号</Label>
                <Input
                  id="chapterNum"
                  type="number"
                  min={1}
                  value={chapterNum}
                  onChange={(e) => setChapterNum(Number(e.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="strandType">脉络类型</Label>
                <Select value={strandType} onValueChange={(v) => setStrandType(v ?? "quest")}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {strandOptions.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        {opt.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="chapterTitle">章节标题</Label>
              <Input
                id="chapterTitle"
                placeholder="输入章节标题..."
                value={chapterTitle}
                onChange={(e) => setChapterTitle(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="configId">模型配置 (可选)</Label>
              <Select value={configId} onValueChange={(v) => setConfigId(v ?? "")}>
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
                onClick={handleGenerate}
                disabled={!chapterTitle.trim()}
              >
                <Wand2 className="w-4 h-4 mr-1" />
                生成
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
        title={`第${chapterNum}章 ${chapterTitle}`}
        content={content}
        isStreaming={isStreaming}
        error={error}
        onSave={handleSave}
        onCancel={cancel}
        onRegenerate={!isStreaming ? handleRegenerate : undefined}
      />
    </>
  )
}
