"use client"

import { useState, useCallback, useRef } from "react"
import { useRouter } from "next/navigation"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { zodResolver } from "@hookform/resolvers/zod"
import {
  Loader2,
  Sparkles,
  ChevronRight,
  ChevronLeft,
  Save,
  Wand2,
  X,
  Check,
  BookOpen,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Progress,
  ProgressLabel,
  ProgressValue,
} from "@/components/ui/progress"
import { wizardApi } from "@/lib/api/wizard"
import type {
  ArcOutlineDTO,
  ArcChapterOutlineDTO,
  WizardSettingsRequest,
  BatchGenerationProgress,
} from "@/types"

// ---------------------------------------------------------------------------
// Step indicator
// ---------------------------------------------------------------------------

const STEPS = [
  { num: 1, label: "基本设定" },
  { num: 2, label: "卷纲预览" },
  { num: 3, label: "保存小说" },
  { num: 4, label: "章纲生成" },
  { num: 5, label: "批量生成" },
]

function StepIndicator({ current }: { current: number }) {
  return (
    <div className="flex items-center justify-center gap-2 mb-8">
      {STEPS.map((step, i) => {
        const isActive = step.num === current
        const isDone = step.num < current
        return (
          <div key={step.num} className="flex items-center gap-2">
            <div
              className={`flex items-center justify-center w-8 h-8 rounded-full text-xs font-medium transition-colors ${
                isActive
                  ? "bg-amber-600 text-white"
                  : isDone
                    ? "bg-green-600 text-white"
                    : "bg-stone-200 text-stone-500 dark:bg-stone-700 dark:text-stone-400"
              }`}
            >
              {isDone ? <Check className="w-4 h-4" /> : step.num}
            </div>
            <span
              className={`text-xs font-medium hidden sm:inline ${
                isActive
                  ? "text-amber-700 dark:text-amber-300"
                  : isDone
                    ? "text-green-700 dark:text-green-300"
                    : "text-stone-400"
              }`}
            >
              {step.label}
            </span>
            {i < STEPS.length - 1 && (
              <div
                className={`w-8 h-px ${
                  step.num < current
                    ? "bg-green-400"
                    : "bg-stone-200 dark:bg-stone-700"
                }`}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Validation schema
// ---------------------------------------------------------------------------

const settingsSchema = z.object({
  novelName: z.string().min(1, "小说名称不能为空"),
  genre: z.string().min(1, "请选择类型"),
  novelType: z.string(),
  tone: z.string(),
  tabooList: z.string(),
  totalOutline: z.string().min(1, "请填写总纲"),
  worldSetting: z.string().min(1, "请填写世界观设定"),
  prompt: z.string(),
})

type SettingsForm = z.infer<typeof settingsSchema>

// ---------------------------------------------------------------------------
// Genre options
// ---------------------------------------------------------------------------

const genreOptions = [
  "玄幻",
  "仙侠",
  "都市",
  "科幻",
  "历史",
  "言情",
  "悬疑",
  "武侠",
  "其他",
]

const RHYTHM_TAG_OPTIONS = [
  { value: "quest", label: "寻宝 (quest)" },
  { value: "fire", label: "高潮 (fire)" },
  { value: "constellation", label: "沉淀 (constellation)" },
]

// ---------------------------------------------------------------------------
// WizardSteps component
// ---------------------------------------------------------------------------

export default function WizardSteps() {
  const router = useRouter()

  // Step state
  const [step, setStep] = useState(1)

  // Step 1 state
  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState<string | null>(null)

  // Step 2 state
  const [arcs, setArcs] = useState<ArcOutlineDTO[]>([])

  // Step 3 state
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [novelId, setNovelId] = useState<string | null>(null)

  // Step 4 state
  const [currentArcIndex, setCurrentArcIndex] = useState(0)
  const [chapterOutlinesMap, setChapterOutlinesMap] = useState<
    Record<number, ArcChapterOutlineDTO[]>
  >({})
  const [streamingArcIndex, setStreamingArcIndex] = useState<number | null>(
    null
  )
  const [streamBuffer, setStreamBuffer] = useState("")
  const [streamError, setStreamError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  // Step 5 state
  const [taskId, setTaskId] = useState<string | null>(null)
  const [batchProgress, setBatchProgress] =
    useState<BatchGenerationProgress | null>(null)
  const [batchError, setBatchError] = useState<string | null>(null)
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // ---- Step 1 form ----
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<SettingsForm>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      novelName: "",
      genre: "",
      novelType: "",
      tone: "",
      tabooList: "",
      totalOutline: "",
      worldSetting: "",
      prompt: "",
    },
  })

  const settingsValues = watch()

  // ---- Helper: tags to JSON ----
  const tagsToJson = useCallback((value: string): string | undefined => {
    if (!value.trim()) return undefined
    const tags = value
      .split(/[,，]/)
      .map((t) => t.trim())
      .filter(Boolean)
    return JSON.stringify(tags)
  }, [])

  // ---- Step 1: Generate volume outlines ----
  const handleGenerateOutlines = async () => {
    setGenerating(true)
    setGenerateError(null)
    try {
      const payload: WizardSettingsRequest = {
        novelName: settingsValues.novelName,
        genre: settingsValues.genre,
        novelType: tagsToJson(settingsValues.novelType),
        tone: tagsToJson(settingsValues.tone),
        tabooList: tagsToJson(settingsValues.tabooList),
        totalOutline: settingsValues.totalOutline,
        worldSetting: settingsValues.worldSetting,
        prompt: settingsValues.prompt,
      }
      const res = await wizardApi.generateVolumeOutlines(payload)
      setArcs(res.data)
      setStep(2)
    } catch (err) {
      setGenerateError(
        err instanceof Error ? err.message : "AI生成卷纲失败，请重试"
      )
    } finally {
      setGenerating(false)
    }
  }

  // ---- Step 2: Edit arc ----
  const updateArc = (
    index: number,
    field: keyof ArcOutlineDTO,
    value: string | number
  ) => {
    setArcs((prev) =>
      prev.map((arc, i) => (i === index ? { ...arc, [field]: value } : arc))
    )
  }

  // ---- Step 3: Save novel ----
  const handleSaveNovel = async () => {
    setSaving(true)
    setSaveError(null)
    try {
      const res = await wizardApi.saveNovel({
        novelName: settingsValues.novelName,
        genre: settingsValues.genre,
        novelType: tagsToJson(settingsValues.novelType),
        tone: tagsToJson(settingsValues.tone),
        tabooList: tagsToJson(settingsValues.tabooList),
        totalOutline: settingsValues.totalOutline,
        worldSetting: settingsValues.worldSetting,
        arcs,
      })
      setNovelId(res.data.novelId)
      setStep(4)
    } catch (err) {
      setSaveError(
        err instanceof Error ? err.message : "保存小说失败，请重试"
      )
    } finally {
      setSaving(false)
    }
  }

  // ---- Step 4: Generate chapter outlines via SSE ----
  const handleGenerateChapterOutlines = useCallback(
    async (arcIndex: number) => {
      if (!novelId) return

      // Cancel any previous stream
      abortRef.current?.abort()

      setStreamingArcIndex(arcIndex)
      setStreamBuffer("")
      setStreamError(null)

      try {
        const response = await wizardApi.generateArcChapterOutlinesStream(
          novelId,
          arcIndex
        )

        if (!response.ok) {
          const text = await response.text()
          throw new Error(text || `HTTP ${response.status}`)
        }

        const reader = response.body?.getReader()
        if (!reader) throw new Error("无法读取流")

        const decoder = new TextDecoder()
        let fullText = ""

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          const chunk = decoder.decode(value, { stream: true })
          fullText += chunk
          setStreamBuffer(fullText)
        }

        // Parse the final JSON
        let parsed: ArcChapterOutlineDTO[]
        try {
          parsed = JSON.parse(fullText)
        } catch {
          // Try to extract JSON from SSE format
          const lines = fullText.split("\n")
          let jsonStr = ""
          for (const line of lines) {
            const dataLine = line.startsWith("data:")
              ? line.slice(5).trim()
              : line.trim()
            if (dataLine && dataLine !== "[DONE]") {
              jsonStr += dataLine
            }
          }
          parsed = JSON.parse(jsonStr)
        }

        setChapterOutlinesMap((prev) => ({ ...prev, [arcIndex]: parsed }))
        setStreamBuffer("")
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") return
        setStreamError(
          err instanceof Error ? err.message : "生成章纲失败"
        )
      } finally {
        setStreamingArcIndex(null)
      }
    },
    [novelId]
  )

  // ---- Step 5: Batch generate ----
  const handleStartBatch = useCallback(async () => {
    if (!novelId) return
    setBatchError(null)
    try {
      const res = await wizardApi.batchGenerate(novelId)
      const newTaskId = res.data
      setTaskId(newTaskId)

      // Start polling
      const poll = async () => {
        try {
          const statusRes = await wizardApi.getBatchStatus(
            novelId,
            newTaskId
          )
          const progress = statusRes.data
          setBatchProgress(progress)

          if (
            progress.status === "COMPLETED" ||
            progress.status === "FAILED" ||
            progress.status === "CANCELLED"
          ) {
            if (pollingRef.current) {
              clearInterval(pollingRef.current)
              pollingRef.current = null
            }
          }
        } catch {
          // polling error, continue
        }
      }

      // Initial fetch
      await poll()
      // Poll every 3 seconds
      pollingRef.current = setInterval(poll, 3000)
    } catch (err) {
      setBatchError(
        err instanceof Error ? err.message : "启动批量生成失败"
      )
    }
  }, [novelId])

  const handleCancelBatch = useCallback(async () => {
    if (!novelId || !taskId) return
    try {
      await wizardApi.batchCancel(novelId, taskId)
      if (pollingRef.current) {
        clearInterval(pollingRef.current)
        pollingRef.current = null
      }
      setBatchProgress((prev) =>
        prev ? { ...prev, status: "CANCELLED" } : null
      )
    } catch {
      // ignore
    }
  }, [novelId, taskId])

  // Navigate to novel detail
  const goToNovel = () => {
    if (novelId) {
      router.push(`/novel/${novelId}`)
    }
  }

  // Check if all arcs have chapter outlines
  const allChapterOutlinesDone = arcs.every(
    (_, i) => chapterOutlinesMap[i] && chapterOutlinesMap[i].length > 0
  )

  // Cleanup on unmount is not strictly needed for this page-level component,
  // but clear intervals if user navigates away
  const cleanup = useCallback(() => {
    abortRef.current?.abort()
    if (pollingRef.current) {
      clearInterval(pollingRef.current)
      pollingRef.current = null
    }
  }, [])

  return (
    <div className="max-w-[800px] mx-auto p-6">
      <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50 mb-2">
        AI向导创建小说
      </h1>
      <p className="text-sm text-stone-500 mb-6">
        通过AI辅助，5步完成小说创建
      </p>

      <StepIndicator current={step} />

      {/* ----------------------------------------------------------------- */}
      {/* Step 1: Settings + Volume Outlines                                */}
      {/* ----------------------------------------------------------------- */}
      {step === 1 && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>基本设定</CardTitle>
              <CardDescription>
                填写小说的基本信息，然后由AI生成卷纲
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Novel Name */}
              <div className="space-y-1.5">
                <Label htmlFor="w-novelName">小说名称 *</Label>
                <Input
                  id="w-novelName"
                  {...register("novelName")}
                  placeholder="请输入小说名称"
                />
                {errors.novelName && (
                  <p className="text-xs text-red-500">
                    {errors.novelName.message}
                  </p>
                )}
              </div>

              {/* Genre */}
              <div className="space-y-1.5">
                <Label htmlFor="w-genre">类型 *</Label>
                <select
                  id="w-genre"
                  {...register("genre")}
                  className="w-full h-8 px-3 py-1 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 text-sm focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none"
                >
                  <option value="">请选择类型</option>
                  {genreOptions.map((g) => (
                    <option key={g} value={g}>
                      {g}
                    </option>
                  ))}
                </select>
                {errors.genre && (
                  <p className="text-xs text-red-500">
                    {errors.genre.message}
                  </p>
                )}
              </div>

              {/* Novel Type */}
              <div className="space-y-1.5">
                <Label htmlFor="w-novelType">故事类型标签</Label>
                <Input
                  id="w-novelType"
                  {...register("novelType")}
                  placeholder="用逗号分隔，如：升级流, 系统流, 种田"
                />
              </div>

              {/* Tone */}
              <div className="space-y-1.5">
                <Label htmlFor="w-tone">基调标签</Label>
                <Input
                  id="w-tone"
                  {...register("tone")}
                  placeholder="用逗号分隔，如：热血, 轻松, 暗黑"
                />
              </div>

              {/* Taboo */}
              <div className="space-y-1.5">
                <Label htmlFor="w-tabooList">禁忌事项</Label>
                <Input
                  id="w-tabooList"
                  {...register("tabooList")}
                  placeholder="用逗号分隔，如：不写后宫, 不写悲剧结局"
                />
              </div>

              {/* Total Outline */}
              <div className="space-y-1.5">
                <Label htmlFor="w-totalOutline">总纲 *</Label>
                <Textarea
                  id="w-totalOutline"
                  {...register("totalOutline")}
                  placeholder="请描述故事主线、主要冲突和结局走向"
                  rows={5}
                />
                {errors.totalOutline && (
                  <p className="text-xs text-red-500">
                    {errors.totalOutline.message}
                  </p>
                )}
              </div>

              {/* World Setting */}
              <div className="space-y-1.5">
                <Label htmlFor="w-worldSetting">世界观设定 *</Label>
                <Textarea
                  id="w-worldSetting"
                  {...register("worldSetting")}
                  placeholder="请描述故事发生的背景、规则体系等"
                  rows={5}
                />
                {errors.worldSetting && (
                  <p className="text-xs text-red-500">
                    {errors.worldSetting.message}
                  </p>
                )}
              </div>

              {/* AI Prompt */}
              <div className="space-y-1.5">
                <Label htmlFor="w-prompt">AI生成提示词</Label>
                <Textarea
                  id="w-prompt"
                  {...register("prompt")}
                  placeholder="可选：给AI的额外提示，如风格要求、节奏偏好等"
                  rows={3}
                />
              </div>
            </CardContent>
          </Card>

          {/* Generate button */}
          <div className="flex items-center gap-3">
            <Button
              onClick={handleGenerateOutlines}
              disabled={generating}
              className="bg-amber-600 hover:bg-amber-700 text-white"
            >
              {generating ? (
                <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
              ) : (
                <Sparkles className="w-4 h-4 mr-1.5" />
              )}
              AI生成卷纲
            </Button>
            {generating && (
              <span className="text-sm text-stone-400">
                AI正在生成卷纲，请稍候...
              </span>
            )}
          </div>
          {generateError && (
            <p className="text-sm text-red-500">{generateError}</p>
          )}
        </div>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* Step 2: Preview / Edit Volume Outlines                            */}
      {/* ----------------------------------------------------------------- */}
      {step === 2 && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>卷纲预览</CardTitle>
              <CardDescription>
                以下是AI生成的卷纲，你可以编辑每卷的名称、大纲和节奏设置
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {arcs.map((arc, index) => (
                <div
                  key={index}
                  className="rounded-lg border border-stone-200 dark:border-stone-700 p-4 space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <Badge variant="secondary">
                      第{arc.startChapter}-{arc.endChapter}章
                    </Badge>
                    <span className="text-xs text-stone-400">
                      共{arc.chapterCount}章
                    </span>
                  </div>

                  {/* Arc Name */}
                  <div className="space-y-1">
                    <Label>卷名</Label>
                    <Input
                      value={arc.arcName}
                      onChange={(e) =>
                        updateArc(index, "arcName", e.target.value)
                      }
                    />
                  </div>

                  {/* Arc Outline */}
                  <div className="space-y-1">
                    <Label>卷纲</Label>
                    <Textarea
                      value={arc.arcOutline}
                      onChange={(e) =>
                        updateArc(index, "arcOutline", e.target.value)
                      }
                      rows={4}
                    />
                  </div>

                  {/* Rhythm settings */}
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <Label>节奏标签</Label>
                      <select
                        value={arc.rhythmTag}
                        onChange={(e) =>
                          updateArc(index, "rhythmTag", e.target.value)
                        }
                        className="w-full h-8 px-3 py-1 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 text-sm outline-none"
                      >
                        {RHYTHM_TAG_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="space-y-1">
                      <Label>
                        节奏强度: {arc.rhythmIntensity}
                      </Label>
                      <input
                        type="range"
                        min={1}
                        max={10}
                        value={arc.rhythmIntensity}
                        onChange={(e) =>
                          updateArc(
                            index,
                            "rhythmIntensity",
                            Number(e.target.value)
                          )
                        }
                        className="w-full accent-amber-600"
                      />
                      <div className="flex justify-between text-[10px] text-stone-400">
                        <span>1</span>
                        <span>10</span>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>

          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setStep(1)}>
              <ChevronLeft className="w-4 h-4 mr-1" />
              上一步
            </Button>
            <Button
              onClick={() => setStep(3)}
              className="bg-amber-600 hover:bg-amber-700 text-white"
            >
              下一步
              <ChevronRight className="w-4 h-4 ml-1" />
            </Button>
          </div>
        </div>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* Step 3: Save Novel                                                */}
      {/* ----------------------------------------------------------------- */}
      {step === 3 && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>保存小说</CardTitle>
              <CardDescription>
                确认信息后保存小说，保存后可以继续生成章纲
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="text-sm space-y-2">
                <p>
                  <span className="font-medium text-stone-600 dark:text-stone-300">
                    小说名称：
                  </span>
                  {settingsValues.novelName}
                </p>
                <p>
                  <span className="font-medium text-stone-600 dark:text-stone-300">
                    类型：
                  </span>
                  {settingsValues.genre}
                </p>
                <p>
                  <span className="font-medium text-stone-600 dark:text-stone-300">
                    卷数：
                  </span>
                  {arcs.length}卷
                </p>
                <p>
                  <span className="font-medium text-stone-600 dark:text-stone-300">
                    总章数：
                  </span>
                  {arcs.reduce((sum, a) => sum + a.chapterCount, 0)}章
                </p>
              </div>
            </CardContent>
          </Card>

          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setStep(2)}>
              <ChevronLeft className="w-4 h-4 mr-1" />
              上一步
            </Button>
            <Button
              onClick={handleSaveNovel}
              disabled={saving}
              className="bg-amber-600 hover:bg-amber-700 text-white"
            >
              {saving ? (
                <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
              ) : (
                <Save className="w-4 h-4 mr-1.5" />
              )}
              保存小说
            </Button>
          </div>
          {saveError && <p className="text-sm text-red-500">{saveError}</p>}
        </div>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* Step 4: Chapter Outlines (per volume)                             */}
      {/* ----------------------------------------------------------------- */}
      {step === 4 && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>章纲生成</CardTitle>
              <CardDescription>
                为每一卷生成章纲，点击按钮开始AI生成
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {arcs.map((arc, index) => {
                const outlines = chapterOutlinesMap[index]
                const isStreaming = streamingArcIndex === index
                const hasOutlines = outlines && outlines.length > 0

                return (
                  <div
                    key={index}
                    className="rounded-lg border border-stone-200 dark:border-stone-700 p-4"
                  >
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <BookOpen className="w-4 h-4 text-amber-600" />
                        <span className="font-medium text-sm">
                          {arc.arcName}
                        </span>
                        <Badge variant="outline" className="text-xs">
                          第{arc.startChapter}-{arc.endChapter}章
                        </Badge>
                      </div>
                      {!hasOutlines && !isStreaming && (
                        <Button
                          size="sm"
                          onClick={() =>
                            handleGenerateChapterOutlines(index)
                          }
                          className="bg-amber-600 hover:bg-amber-700 text-white"
                        >
                          <Wand2 className="w-3.5 h-3.5 mr-1" />
                          生成章纲
                        </Button>
                      )}
                      {hasOutlines && !isStreaming && (
                        <Badge
                          variant="default"
                          className="bg-green-600 text-white"
                        >
                          已生成
                        </Badge>
                      )}
                    </div>

                    {/* Streaming buffer */}
                    {isStreaming && (
                      <div className="mt-2 p-3 rounded bg-stone-50 dark:bg-stone-800/50 text-xs text-stone-600 dark:text-stone-300 whitespace-pre-wrap max-h-[200px] overflow-y-auto">
                        {streamBuffer || (
                          <span className="flex items-center gap-1.5 text-amber-600">
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            AI正在生成章纲...
                          </span>
                        )}
                      </div>
                    )}

                    {/* Generated outlines */}
                    {hasOutlines && (
                      <div className="space-y-2 mt-2">
                        {outlines.map((ch) => (
                          <div
                            key={ch.chapterNum}
                            className="flex items-start gap-3 p-2 rounded bg-stone-50 dark:bg-stone-800/50"
                          >
                            <span className="text-xs font-mono text-amber-700 dark:text-amber-300 shrink-0 w-16">
                              第{ch.chapterNum}章
                            </span>
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium">
                                {ch.chapterTitle}
                              </p>
                              <p className="text-xs text-stone-500 mt-0.5 line-clamp-2">
                                {ch.outline}
                              </p>
                              {ch.implementationIdeas &&
                                ch.implementationIdeas.length > 0 && (
                                  <div className="flex flex-wrap gap-1 mt-1">
                                    {ch.implementationIdeas.map(
                                      (idea, i) => (
                                        <Badge
                                          key={i}
                                          variant="secondary"
                                          className="text-[10px]"
                                        >
                                          {idea}
                                        </Badge>
                                      )
                                    )}
                                  </div>
                                )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })}
            </CardContent>
          </Card>

          {streamError && (
            <p className="text-sm text-red-500">{streamError}</p>
          )}

          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setStep(3)}>
              <ChevronLeft className="w-4 h-4 mr-1" />
              上一步
            </Button>
            <Button
              onClick={() => setStep(5)}
              disabled={!allChapterOutlinesDone}
              className="bg-amber-600 hover:bg-amber-700 text-white"
            >
              下一步
              <ChevronRight className="w-4 h-4 ml-1" />
            </Button>
          </div>
        </div>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* Step 5: Batch Generate                                            */}
      {/* ----------------------------------------------------------------- */}
      {step === 5 && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>批量生成正文</CardTitle>
              <CardDescription>
                开始批量生成所有章节的正文内容
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {!batchProgress && !taskId && (
                <div className="text-center py-8">
                  <p className="text-sm text-stone-500 mb-4">
                    共{arcs.reduce((s, a) => s + a.chapterCount, 0)}章需要生成
                  </p>
                  <Button
                    onClick={handleStartBatch}
                    className="bg-amber-600 hover:bg-amber-700 text-white"
                  >
                    <Sparkles className="w-4 h-4 mr-1.5" />
                    开始批量生成
                  </Button>
                </div>
              )}

              {batchProgress && (
                <div className="space-y-4">
                  {/* Status badge */}
                  <div className="flex items-center justify-between">
                    <Badge
                      variant={
                        batchProgress.status === "COMPLETED"
                          ? "default"
                          : batchProgress.status === "FAILED"
                            ? "destructive"
                            : batchProgress.status === "CANCELLED"
                              ? "secondary"
                              : "outline"
                      }
                      className={
                        batchProgress.status === "COMPLETED"
                          ? "bg-green-600 text-white"
                          : ""
                      }
                    >
                      {batchProgress.status === "GENERATING"
                        ? "生成中"
                        : batchProgress.status === "COMPLETED"
                          ? "已完成"
                          : batchProgress.status === "CANCELLED"
                            ? "已取消"
                            : "失败"}
                    </Badge>
                    {batchProgress.status === "GENERATING" && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={handleCancelBatch}
                      >
                        <X className="w-3.5 h-3.5 mr-1" />
                        取消
                      </Button>
                    )}
                  </div>

                  {/* Progress bar */}
                  <div className="space-y-2">
                    <Progress
                      value={
                        batchProgress.totalChapters > 0
                          ? Math.round(
                              (batchProgress.completedChapters /
                                batchProgress.totalChapters) *
                                100
                            )
                          : 0
                      }
                    >
                      <ProgressLabel>生成进度</ProgressLabel>
                      <ProgressValue />
                    </Progress>
                  </div>

                  {/* Details */}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                    <div className="p-2 rounded bg-stone-50 dark:bg-stone-800/50">
                      <p className="text-lg font-bold text-amber-700 dark:text-amber-300">
                        {batchProgress.completedChapters}
                      </p>
                      <p className="text-xs text-stone-400">已完成</p>
                    </div>
                    <div className="p-2 rounded bg-stone-50 dark:bg-stone-800/50">
                      <p className="text-lg font-bold text-stone-600 dark:text-stone-300">
                        {batchProgress.totalChapters}
                      </p>
                      <p className="text-xs text-stone-400">总章数</p>
                    </div>
                    <div className="p-2 rounded bg-stone-50 dark:bg-stone-800/50">
                      <p className="text-lg font-bold text-red-600">
                        {batchProgress.failedChapters}
                      </p>
                      <p className="text-xs text-stone-400">失败</p>
                    </div>
                    <div className="p-2 rounded bg-stone-50 dark:bg-stone-800/50">
                      <p className="text-lg font-bold text-blue-600">
                        {batchProgress.currentChapterNum || "-"}
                      </p>
                      <p className="text-xs text-stone-400">当前章节</p>
                    </div>
                  </div>

                  {/* Current phase */}
                  {batchProgress.currentPhase && (
                    <p className="text-xs text-stone-400">
                      当前阶段：{batchProgress.currentPhase}
                    </p>
                  )}

                  {/* Error message */}
                  {batchProgress.errorMessage && (
                    <p className="text-xs text-red-500">
                      {batchProgress.errorMessage}
                    </p>
                  )}

                  {/* Completed action */}
                  {batchProgress.status === "COMPLETED" && (
                    <div className="flex justify-center pt-2">
                      <Button
                        onClick={goToNovel}
                        className="bg-amber-600 hover:bg-amber-700 text-white"
                      >
                        查看小说
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {batchError && (
            <p className="text-sm text-red-500">{batchError}</p>
          )}

          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setStep(4)}>
              <ChevronLeft className="w-4 h-4 mr-1" />
              上一步
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
