"use client"

import { useState, useCallback, useRef } from "react"
import { useRouter } from "next/navigation"
import {
  Palette,
  Upload,
  Trash2,
  Globe,
  Lock,
  ChevronDown,
  ChevronUp,
  Loader2,
  Eye,
  FileText,
  BarChart3,
} from "lucide-react"
import useSWR from "swr"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Skeleton } from "@/components/ui/skeleton"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { EmptyState } from "@/components/shared/EmptyState"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"
import { styleApi } from "@/lib/api/style"
import { useAuth } from "@/hooks/useAuth"
import type { StyleCard } from "@/types"

const MIN_TEXT_LENGTH = 5000

/* ------------------------------------------------------------------ */
/* Status helpers                                                      */
/* ------------------------------------------------------------------ */

const statusMap: Record<number, { label: string; className: string }> = {
  0: {
    label: "分析中",
    className:
      "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200",
  },
  1: {
    label: "可用",
    className:
      "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  },
  2: {
    label: "分析失败",
    className: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  },
}

/* ------------------------------------------------------------------ */
/* MetricItem component                                               */
/* ------------------------------------------------------------------ */

function MetricItem({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="text-center">
      <div className="text-sm font-semibold text-amber-600 dark:text-amber-400">
        {value}
      </div>
      <div className="text-[10px] text-stone-400 mt-0.5">{label}</div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* StyleCardItem component                                             */
/* ------------------------------------------------------------------ */

function StyleCardItem({
  card,
  onUse,
  onDelete,
  onTogglePublic,
  onPreview,
}: {
  card: StyleCard
  onUse: () => void
  onDelete: () => void
  onTogglePublic: () => void
  onPreview: () => void
}) {
  const status = statusMap[card.status] ?? statusMap[0]
  const metrics = card.statsJson

  return (
    <Card className="flex flex-col">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-medium leading-tight line-clamp-2">
            {card.name}
          </CardTitle>
          <Badge variant="secondary" className={status.className + " shrink-0"}>
            {status.label}
          </Badge>
        </div>
        {card.sourceNovel && (
          <p className="text-xs text-stone-400 mt-1 truncate">
            来源: {card.sourceNovel}
          </p>
        )}
      </CardHeader>

      <CardContent className="flex-1 flex flex-col gap-3">
        {/* Metrics grid */}
        {metrics && (
          <div className="grid grid-cols-4 gap-2">
            <MetricItem
              label="句均长度"
              value={metrics.avgSentenceLength?.toFixed(1) ?? "-"}
            />
            <MetricItem
              label="短句率"
              value={
                metrics.shortSentenceRatio != null
                  ? `${(metrics.shortSentenceRatio * 100).toFixed(0)}%`
                  : "-"
              }
            />
            <MetricItem
              label="对话率"
              value={
                metrics.dialogueRatio != null
                  ? `${(metrics.dialogueRatio * 100).toFixed(0)}%`
                  : "-"
              }
            />
            <MetricItem
              label="段均长度"
              value={metrics.avgParagraphLength?.toFixed(1) ?? "-"}
            />
          </div>
        )}

        {/* Sample preview */}
        {card.samplesJson && card.samplesJson.length > 0 && (
          <p className="text-xs text-stone-500 dark:text-stone-400 line-clamp-3 leading-relaxed">
            {card.samplesJson[0].text}
          </p>
        )}

        {/* Actions */}
        <div className="flex items-center gap-2 mt-auto pt-2 border-t border-stone-100 dark:border-stone-700">
          <Button
            size="sm"
            className="bg-amber-600 hover:bg-amber-700 text-white h-7 text-xs"
            onClick={onUse}
            disabled={card.status !== 1}
          >
            使用
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs"
            onClick={onPreview}
          >
            <Eye className="w-3 h-3 mr-1" />
            预览
          </Button>
          <div className="flex-1" />
          <Button
            variant="ghost"
            size="sm"
            className="h-7 w-7 p-0 text-stone-400 hover:text-stone-600"
            onClick={onTogglePublic}
            title={card.isPublic ? "设为私密" : "设为公开"}
          >
            {card.isPublic ? (
              <Globe className="w-3.5 h-3.5" />
            ) : (
              <Lock className="w-3.5 h-3.5" />
            )}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-7 w-7 p-0 text-stone-400 hover:text-red-500"
            onClick={onDelete}
            title="删除"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

/* ------------------------------------------------------------------ */
/* DetailDialog component                                             */
/* ------------------------------------------------------------------ */

function DetailDialog({
  card,
  open,
  onClose,
}: {
  card: StyleCard | null
  open: boolean
  onClose: () => void
}) {
  if (!open || !card) return null

  const metrics = card.statsJson

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-stone-800 rounded-xl shadow-xl max-w-2xl w-full mx-4 max-h-[80vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-bold text-stone-900 dark:text-stone-50">
              {card.name}
            </h2>
            <Button variant="ghost" size="sm" onClick={onClose}>
              关闭
            </Button>
          </div>

          {card.sourceNovel && (
            <p className="text-sm text-stone-500 mb-4">
              来源: {card.sourceNovel}
              {card.authorName ? ` - ${card.authorName}` : ""}
            </p>
          )}

          {/* Metrics */}
          {metrics && (
            <div className="mb-6">
              <h3 className="text-sm font-semibold text-stone-900 dark:text-stone-50 mb-3 flex items-center gap-1.5">
                <BarChart3 className="w-4 h-4 text-amber-600" />
                量化指标
              </h3>
              <div className="grid grid-cols-4 gap-3">
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.avgSentenceLength?.toFixed(1) ?? "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">句均长度</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.shortSentenceRatio != null
                        ? `${(metrics.shortSentenceRatio * 100).toFixed(0)}%`
                        : "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">短句率</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.dialogueRatio != null
                        ? `${(metrics.dialogueRatio * 100).toFixed(0)}%`
                        : "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">对话率</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.avgParagraphLength?.toFixed(1) ?? "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">段均长度</div>
                  </CardContent>
                </Card>
              </div>
              <div className="grid grid-cols-3 gap-3 mt-3">
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.totalWords?.toLocaleString() ?? "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">总字数</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.totalSentences?.toLocaleString() ?? "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">总句数</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="p-3 text-center">
                    <div className="text-lg font-bold text-amber-600 dark:text-amber-400">
                      {metrics.totalParagraphs?.toLocaleString() ?? "-"}
                    </div>
                    <div className="text-[10px] text-stone-400">总段落</div>
                  </CardContent>
                </Card>
              </div>
              {metrics.distinctiveWords && metrics.distinctiveWords.length > 0 && (
                <div className="mt-3">
                  <p className="text-xs text-stone-500 mb-1.5">特征词:</p>
                  <div className="flex flex-wrap gap-1.5">
                    {metrics.distinctiveWords.map((w) => (
                      <span
                        key={w}
                        className="inline-block text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
                      >
                        {w}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Samples */}
          {card.samplesJson && card.samplesJson.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-stone-900 dark:text-stone-50 mb-3 flex items-center gap-1.5">
                <FileText className="w-4 h-4 text-amber-600" />
                原文样本
              </h3>
              <div className="space-y-3">
                {card.samplesJson.map((sample, idx) => (
                  <div
                    key={idx}
                    className="rounded-lg border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800/50 p-4"
                  >
                    <div className="flex items-center gap-2 mb-2">
                      <Badge variant="outline" className="text-[10px]">
                        {sample.type}
                      </Badge>
                    </div>
                    <p className="text-sm text-stone-600 dark:text-stone-300 whitespace-pre-wrap leading-relaxed">
                      {sample.text}
                    </p>
                    {sample.annotation && (
                      <p className="text-xs text-stone-400 mt-2 italic">
                        {sample.annotation}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Main page                                                           */
/* ------------------------------------------------------------------ */

export default function StyleCardsPage() {
  useAuth()
  const router = useRouter()

  // Tab state
  const [activeTab, setActiveTab] = useState<"mine" | "public">("mine")

  // Create section state
  const [createOpen, setCreateOpen] = useState(false)
  const [createMode, setCreateMode] = useState<"text" | "file">("text")
  const [novelName, setNovelName] = useState("")
  const [textInput, setTextInput] = useState("")
  const [configId, setConfigId] = useState("")
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Detail dialog state
  const [detailCard, setDetailCard] = useState<StyleCard | null>(null)

  // Delete confirmation state
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  // Fetch user cards
  const {
    data: userCardsData,
    isLoading: userLoading,
    mutate: mutateUser,
  } = useSWR(activeTab === "mine" ? "style-cards-user" : null, () =>
    styleApi.getUserCards()
  )
  const userCards = userCardsData?.data ?? []

  // Fetch public cards
  const {
    data: publicCardsData,
    isLoading: publicLoading,
    mutate: mutatePublic,
  } = useSWR(activeTab === "public" ? "style-cards-public" : null, () =>
    styleApi.getPublicCards()
  )
  const publicCards = publicCardsData?.data ?? []

  const cards = activeTab === "mine" ? userCards : publicCards
  const isLoading = activeTab === "mine" ? userLoading : publicLoading
  const mutate = activeTab === "mine" ? mutateUser : mutatePublic

  /* ---- Create from text ---- */
  const handleCreateFromText = useCallback(async () => {
    if (!novelName.trim()) {
      setCreateError("请输入小说名称")
      return
    }
    if (textInput.length < MIN_TEXT_LENGTH) {
      setCreateError(`文本至少需要 ${MIN_TEXT_LENGTH} 个字符，当前 ${textInput.length} 个`)
      return
    }

    setCreating(true)
    setCreateError(null)
    try {
      await styleApi.createFromText({
        novelName: novelName.trim(),
        text: textInput,
        configId: configId.trim() || undefined,
      })
      setNovelName("")
      setTextInput("")
      setConfigId("")
      setCreateOpen(false)
      mutate()
    } catch (err) {
      setCreateError(
        err instanceof Error ? err.message : "创建失败，请重试"
      )
    } finally {
      setCreating(false)
    }
  }, [novelName, textInput, configId, mutate])

  /* ---- Create from file ---- */
  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      if (!file.name.endsWith(".txt")) {
        setCreateError("仅支持 .txt 文件")
        return
      }

      if (!novelName.trim()) {
        setCreateError("请先输入小说名称")
        return
      }

      setCreating(true)
      setCreateError(null)
      try {
        await styleApi.createFromFile(
          file,
          novelName.trim(),
          configId.trim() || undefined
        )
        setNovelName("")
        setConfigId("")
        setCreateOpen(false)
        if (fileInputRef.current) fileInputRef.current.value = ""
        mutate()
      } catch (err) {
        setCreateError(
          err instanceof Error ? err.message : "上传失败，请重试"
        )
      } finally {
        setCreating(false)
      }
    },
    [novelName, configId, mutate]
  )

  /* ---- Delete ---- */
  const handleDelete = useCallback((cardId: string) => {
    setDeletingId(cardId)
    setDeleteOpen(true)
  }, [])

  const confirmDelete = useCallback(async () => {
    if (!deletingId) return
    try {
      await styleApi.delete(deletingId)
      mutate()
    } finally {
      setDeleteOpen(false)
      setDeletingId(null)
    }
  }, [deletingId, mutate])

  /* ---- Toggle public ---- */
  const handleTogglePublic = useCallback(
    async (card: StyleCard) => {
      try {
        await styleApi.updatePublic(card.cardId, !card.isPublic)
        mutate()
      } catch {
        // ignore
      }
    },
    [mutate]
  )

  /* ---- Use card ---- */
  const handleUse = useCallback(
    (cardId: string) => {
      router.push(`/create?styleCardId=${cardId}`)
    },
    [router]
  )

  /* ---- Preview ---- */
  const handlePreview = useCallback(
    async (card: StyleCard) => {
      try {
        const res = await styleApi.getDetail(card.cardId)
        setDetailCard(res.data)
      } catch {
        setDetailCard(card)
      }
    },
    []
  )

  return (
    <div className="max-w-5xl mx-auto p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-amber-100 dark:bg-amber-900 flex items-center justify-center">
            <Palette className="w-5 h-5 text-amber-700 dark:text-amber-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50">
              风格卡库
            </h1>
            <p className="text-xs text-stone-400">
              分析和管理写作风格卡片
            </p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as "mine" | "public")}
      >
        <div className="flex items-center justify-between mb-4">
          <TabsList>
            <TabsTrigger value="mine">我的风格卡</TabsTrigger>
            <TabsTrigger value="public">公共风格卡</TabsTrigger>
          </TabsList>

          {activeTab === "mine" && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setCreateOpen(!createOpen)}
            >
              {createOpen ? (
                <ChevronUp className="w-3.5 h-3.5 mr-1" />
              ) : (
                <ChevronDown className="w-3.5 h-3.5 mr-1" />
              )}
              创建风格卡
            </Button>
          )}
        </div>

        {/* Create section (only for "mine" tab) */}
        {activeTab === "mine" && createOpen && (
          <Card className="mb-6">
            <CardHeader>
              <CardTitle className="text-sm">创建新风格卡</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Mode toggle */}
              <div className="flex gap-2">
                <Button
                  variant={createMode === "text" ? "default" : "outline"}
                  size="sm"
                  onClick={() => setCreateMode("text")}
                  className={
                    createMode === "text"
                      ? "bg-amber-600 hover:bg-amber-700 text-white"
                      : ""
                  }
                >
                  粘贴文本
                </Button>
                <Button
                  variant={createMode === "file" ? "default" : "outline"}
                  size="sm"
                  onClick={() => setCreateMode("file")}
                  className={
                    createMode === "file"
                      ? "bg-amber-600 hover:bg-amber-700 text-white"
                      : ""
                  }
                >
                  <Upload className="w-3.5 h-3.5 mr-1" />
                  上传文件
                </Button>
              </div>

              {/* Novel name input */}
              <div className="space-y-1.5">
                <Label htmlFor="novelName">小说名称 *</Label>
                <Input
                  id="novelName"
                  value={novelName}
                  onChange={(e) => setNovelName(e.target.value)}
                  placeholder="请输入原作小说名称"
                />
              </div>

              {/* Config ID (optional) */}
              <div className="space-y-1.5">
                <Label htmlFor="configId">LLM 配置 ID（可选）</Label>
                <Input
                  id="configId"
                  value={configId}
                  onChange={(e) => setConfigId(e.target.value)}
                  placeholder="留空使用默认配置"
                />
              </div>

              {createMode === "text" ? (
                <>
                  {/* Text textarea */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="styleText">原文文本 *</Label>
                      <span
                        className={
                          "text-xs " +
                          (textInput.length >= MIN_TEXT_LENGTH
                            ? "text-green-600"
                            : "text-stone-400")
                        }
                      >
                        {textInput.length} / {MIN_TEXT_LENGTH}
                      </span>
                    </div>
                    <Textarea
                      id="styleText"
                      value={textInput}
                      onChange={(e) => setTextInput(e.target.value)}
                      placeholder={`粘贴至少 ${MIN_TEXT_LENGTH} 个字符的小说原文，用于分析写作风格...`}
                      rows={10}
                    />
                  </div>

                  <Button
                    onClick={handleCreateFromText}
                    disabled={creating}
                    className="bg-amber-600 hover:bg-amber-700 text-white"
                  >
                    {creating && (
                      <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                    )}
                    开始分析
                  </Button>
                </>
              ) : (
                <>
                  {/* File upload */}
                  <div className="space-y-1.5">
                    <Label>上传 .txt 文件 *</Label>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".txt"
                      onChange={handleFileChange}
                      className="block w-full text-sm text-stone-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-amber-50 file:text-amber-700 hover:file:bg-amber-100 dark:file:bg-amber-900 dark:file:text-amber-200"
                    />
                    <p className="text-xs text-stone-400">
                      上传 .txt 格式的小说原文文件
                    </p>
                  </div>
                </>
              )}

              {createError && (
                <p className="text-xs text-red-500">{createError}</p>
              )}
            </CardContent>
          </Card>
        )}

        {/* Content panels */}
        <TabsContent value="mine">
          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {Array.from({ length: 6 }).map((_, i) => (
                <Card key={i}>
                  <CardContent className="p-4 space-y-3">
                    <Skeleton className="h-5 w-3/4" />
                    <Skeleton className="h-4 w-1/2" />
                    <div className="grid grid-cols-4 gap-2">
                      {Array.from({ length: 4 }).map((_, j) => (
                        <Skeleton key={j} className="h-10 w-full" />
                      ))}
                    </div>
                    <Skeleton className="h-12 w-full" />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : userCards.length === 0 ? (
            <EmptyState
              icon={<Palette className="w-6 h-6 text-stone-400" />}
              title="还没有风格卡"
              description="创建你的第一张风格卡，分析和复刻喜爱的写作风格"
              action={
                <Button
                  className="bg-amber-600 hover:bg-amber-700 text-white"
                  onClick={() => setCreateOpen(true)}
                >
                  创建风格卡
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {userCards.map((card) => (
                <StyleCardItem
                  key={card.cardId}
                  card={card}
                  onUse={() => handleUse(card.cardId)}
                  onDelete={() => handleDelete(card.cardId)}
                  onTogglePublic={() => handleTogglePublic(card)}
                  onPreview={() => handlePreview(card)}
                />
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="public">
          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {Array.from({ length: 6 }).map((_, i) => (
                <Card key={i}>
                  <CardContent className="p-4 space-y-3">
                    <Skeleton className="h-5 w-3/4" />
                    <Skeleton className="h-4 w-1/2" />
                    <div className="grid grid-cols-4 gap-2">
                      {Array.from({ length: 4 }).map((_, j) => (
                        <Skeleton key={j} className="h-10 w-full" />
                      ))}
                    </div>
                    <Skeleton className="h-12 w-full" />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : publicCards.length === 0 ? (
            <EmptyState
              icon={<Globe className="w-6 h-6 text-stone-400" />}
              title="暂无公共风格卡"
              description="还没有用户公开分享的风格卡"
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {publicCards.map((card) => (
                <StyleCardItem
                  key={card.cardId}
                  card={card}
                  onUse={() => handleUse(card.cardId)}
                  onDelete={() => handleDelete(card.cardId)}
                  onTogglePublic={() => handleTogglePublic(card)}
                  onPreview={() => handlePreview(card)}
                />
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* Detail preview dialog */}
      <DetailDialog
        card={detailCard}
        open={!!detailCard}
        onClose={() => setDetailCard(null)}
      />

      {/* Delete confirmation dialog */}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除风格卡"
        description="确定要删除这张风格卡吗？此操作不可撤销。"
        onConfirm={confirmDelete}
        confirmText="删除"
        variant="destructive"
      />
    </div>
  )
}
