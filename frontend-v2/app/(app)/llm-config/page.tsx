"use client"

import { useState, useCallback } from "react"
import {
  Settings2,
  Plus,
  Pencil,
  Trash2,
  Zap,
  Loader2,
  CheckCircle2,
  XCircle,
  Server,
} from "lucide-react"
import useSWR from "swr"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { EmptyState } from "@/components/shared/EmptyState"
import { ConfirmDialog } from "@/components/shared/ConfirmDialog"
import { llmConfigApi } from "@/lib/api/llm-config"
import { useAuth } from "@/hooks/useAuth"
import type {
  LlmConfigVO,
  CreateLlmConfigRequest,
  UpdateLlmConfigRequest,
} from "@/types"

/* ------------------------------------------------------------------ */
/* Quick-fill templates                                                */
/* ------------------------------------------------------------------ */

interface Template {
  name: string
  provider: string
  apiBaseUrl: string
  authType: string
}

const templates: Template[] = [
  {
    name: "Ollama (本地)",
    provider: "ollama",
    apiBaseUrl: "http://localhost:11434",
    authType: "none",
  },
  {
    name: "DeepSeek",
    provider: "deepseek",
    apiBaseUrl: "https://api.deepseek.com",
    authType: "Bearer",
  },
  {
    name: "OpenAI",
    provider: "openai",
    apiBaseUrl: "https://api.openai.com/v1",
    authType: "Bearer",
  },
  {
    name: "智谱 GLM",
    provider: "zhipu",
    apiBaseUrl: "https://open.bigmodel.cn/api/paas/v4",
    authType: "Bearer",
  },
  {
    name: "通义千问",
    provider: "tongyi",
    apiBaseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    authType: "Bearer",
  },
  {
    name: "Moonshot",
    provider: "moonshot",
    apiBaseUrl: "https://api.moonshot.cn/v1",
    authType: "Bearer",
  },
  {
    name: "文心一言",
    provider: "wenxin",
    apiBaseUrl:
      "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop",
    authType: "Bearer",
  },
]

const authTypes = ["Bearer", "X-Api-Key", "Anthropic", "none"]

const usageTypes = [
  { value: "all", label: "通用" },
  { value: "outline", label: "大纲" },
  { value: "writing", label: "写作" },
  { value: "summary", label: "摘要" },
  { value: "audit", label: "审核" },
  { value: "embedding", label: "嵌入" },
]

/* ------------------------------------------------------------------ */
/* Form data type                                                      */
/* ------------------------------------------------------------------ */

interface FormData {
  configName: string
  provider: string
  apiBaseUrl: string
  modelName: string
  authType: string
  usageType: string
  apiKey: string
  maxTokens: string
  temperature: string
  topP: string
  isActive: number
}

const emptyForm: FormData = {
  configName: "",
  provider: "",
  apiBaseUrl: "",
  modelName: "",
  authType: "Bearer",
  usageType: "all",
  apiKey: "",
  maxTokens: "4096",
  temperature: "0.7",
  topP: "0.9",
  isActive: 1,
}

/* ------------------------------------------------------------------ */
/* LlmConfigCard component                                             */
/* ------------------------------------------------------------------ */

function LlmConfigCard({
  config,
  onEdit,
  onDelete,
  onTest,
  testing,
}: {
  config: LlmConfigVO
  onEdit: () => void
  onDelete: () => void
  onTest: () => void
  testing: boolean
}) {
  const isActive = config.isActive === 1

  const usageLabel =
    usageTypes.find((u) => u.value === config.usageType)?.label ??
    config.usageType

  return (
    <Card className="flex flex-col">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-sm font-medium leading-tight line-clamp-1">
            {config.configName}
          </CardTitle>
          <Badge
            variant="secondary"
            className={
              isActive
                ? "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200 shrink-0"
                : "bg-stone-100 text-stone-600 dark:bg-stone-700 dark:text-stone-300 shrink-0"
            }
          >
            {isActive ? "启用" : "禁用"}
          </Badge>
        </div>
        <p className="text-xs text-stone-400 mt-1">
          {config.provider} &middot; {config.modelName}
        </p>
      </CardHeader>

      <CardContent className="flex-1 flex flex-col gap-2">
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
          <div className="text-stone-500 dark:text-stone-400 truncate">
            <span className="text-stone-400">地址:</span>{" "}
            {config.apiBaseUrl}
          </div>
          <div className="text-stone-500 dark:text-stone-400">
            <span className="text-stone-400">认证:</span> {config.authType}
          </div>
          <div className="text-stone-500 dark:text-stone-400">
            <span className="text-stone-400">用途:</span> {usageLabel}
          </div>
          <div className="text-stone-500 dark:text-stone-400">
            <span className="text-stone-400">温度:</span>{" "}
            {config.temperature}
          </div>
        </div>

        <div className="flex items-center gap-2 mt-auto pt-2 border-t border-stone-100 dark:border-stone-700">
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs"
            onClick={onTest}
            disabled={testing}
          >
            {testing ? (
              <Loader2 className="w-3 h-3 mr-1 animate-spin" />
            ) : (
              <Zap className="w-3 h-3 mr-1" />
            )}
            测试
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs"
            onClick={onEdit}
          >
            <Pencil className="w-3 h-3 mr-1" />
            编辑
          </Button>
          <div className="flex-1" />
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
/* ConfigFormDialog component                                          */
/* ------------------------------------------------------------------ */

function ConfigFormDialog({
  open,
  onOpenChange,
  editingConfig,
  onSubmit,
  submitting,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  editingConfig: LlmConfigVO | null
  onSubmit: (data: CreateLlmConfigRequest | UpdateLlmConfigRequest) => void
  submitting: boolean
}) {
  const [form, setForm] = useState<FormData>(emptyForm)
  const [initialized, setInitialized] = useState(false)

  // Reset form when dialog opens
  if (open && !initialized) {
    if (editingConfig) {
      setForm({
        configName: editingConfig.configName,
        provider: editingConfig.provider,
        apiBaseUrl: editingConfig.apiBaseUrl,
        modelName: editingConfig.modelName,
        authType: editingConfig.authType || "Bearer",
        usageType: editingConfig.usageType || "all",
        apiKey: editingConfig.apiKey,
        maxTokens: String(editingConfig.maxTokens ?? 4096),
        temperature: String(editingConfig.temperature ?? 0.7),
        topP: String(editingConfig.topP ?? 0.9),
        isActive: editingConfig.isActive,
      })
    } else {
      setForm(emptyForm)
    }
    setInitialized(true)
  }

  if (!open && initialized) {
    setInitialized(false)
  }

  const set = <K extends keyof FormData>(key: K, value: FormData[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const applyTemplate = (t: Template) => {
    setForm((prev) => ({
      ...prev,
      provider: t.provider,
      apiBaseUrl: t.apiBaseUrl,
      authType: t.authType,
      configName: prev.configName || t.name,
    }))
  }

  const handleSubmit = () => {
    if (!form.configName.trim()) {
      toast.error("请输入配置名称")
      return
    }
    if (!form.provider.trim()) {
      toast.error("请输入供应商")
      return
    }
    if (!form.apiBaseUrl.trim()) {
      toast.error("请输入 API 地址")
      return
    }
    if (!form.modelName.trim()) {
      toast.error("请输入模型名称")
      return
    }

    const data = {
      configName: form.configName.trim(),
      provider: form.provider.trim(),
      apiBaseUrl: form.apiBaseUrl.trim(),
      modelName: form.modelName.trim(),
      authType: form.authType,
      usageType: form.usageType,
      apiKey: form.apiKey,
      maxTokens: parseInt(form.maxTokens) || 4096,
      temperature: parseFloat(form.temperature) || 0.7,
      topP: parseFloat(form.topP) || 0.9,
      isActive: form.isActive,
    }
    onSubmit(data)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {editingConfig ? "编辑配置" : "新建配置"}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Quick-fill templates (only for create) */}
          {!editingConfig && (
            <div className="space-y-1.5">
              <Label className="text-xs text-stone-500">快速填充模板</Label>
              <div className="flex flex-wrap gap-1.5">
                {templates.map((t) => (
                  <Button
                    key={t.provider}
                    variant="outline"
                    size="sm"
                    className="h-7 text-xs"
                    onClick={() => applyTemplate(t)}
                  >
                    {t.name}
                  </Button>
                ))}
              </div>
            </div>
          )}

          {/* Config name */}
          <div className="space-y-1.5">
            <Label htmlFor="configName">配置名称 *</Label>
            <Input
              id="configName"
              value={form.configName}
              onChange={(e) => set("configName", e.target.value)}
              placeholder="例如: DeepSeek 主力模型"
            />
          </div>

          {/* Provider + Model name */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="provider">供应商 *</Label>
              <Input
                id="provider"
                value={form.provider}
                onChange={(e) => set("provider", e.target.value)}
                placeholder="例如: deepseek"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="modelName">模型名称 *</Label>
              <Input
                id="modelName"
                value={form.modelName}
                onChange={(e) => set("modelName", e.target.value)}
                placeholder="例如: deepseek-chat"
              />
            </div>
          </div>

          {/* API Base URL */}
          <div className="space-y-1.5">
            <Label htmlFor="apiBaseUrl">API 地址 *</Label>
            <Input
              id="apiBaseUrl"
              value={form.apiBaseUrl}
              onChange={(e) => set("apiBaseUrl", e.target.value)}
              placeholder="https://api.deepseek.com"
            />
          </div>

          {/* Auth type + Usage type */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="authType">认证方式</Label>
              <select
                id="authType"
                value={form.authType}
                onChange={(e) => set("authType", e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                {authTypes.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="usageType">用途</Label>
              <select
                id="usageType"
                value={form.usageType}
                onChange={(e) => set("usageType", e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                {usageTypes.map((u) => (
                  <option key={u.value} value={u.value}>
                    {u.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* API Key */}
          <div className="space-y-1.5">
            <Label htmlFor="apiKey">API Key</Label>
            <Input
              id="apiKey"
              type="password"
              value={form.apiKey}
              onChange={(e) => set("apiKey", e.target.value)}
              placeholder={editingConfig ? "留空表示不修改" : "sk-..."}
            />
          </div>

          {/* Numeric params */}
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="maxTokens">最大 Token</Label>
              <Input
                id="maxTokens"
                type="number"
                value={form.maxTokens}
                onChange={(e) => set("maxTokens", e.target.value)}
                min={1}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="temperature">温度</Label>
              <Input
                id="temperature"
                type="number"
                value={form.temperature}
                onChange={(e) => set("temperature", e.target.value)}
                min={0}
                max={2}
                step={0.1}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="topP">Top P</Label>
              <Input
                id="topP"
                type="number"
                value={form.topP}
                onChange={(e) => set("topP", e.target.value)}
                min={0}
                max={1}
                step={0.05}
              />
            </div>
          </div>

          {/* Active toggle */}
          <div className="flex items-center gap-2">
            <input
              id="isActive"
              type="checkbox"
              checked={form.isActive === 1}
              onChange={(e) => set("isActive", e.target.checked ? 1 : 0)}
              className="h-4 w-4 rounded border-stone-300"
            />
            <Label htmlFor="isActive" className="cursor-pointer">
              启用此配置
            </Label>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={submitting}
          >
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={submitting}
            className="bg-amber-600 hover:bg-amber-700 text-white"
          >
            {submitting && (
              <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
            )}
            {editingConfig ? "保存" : "创建"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ------------------------------------------------------------------ */
/* Main page                                                           */
/* ------------------------------------------------------------------ */

export default function LlmConfigPage() {
  useAuth()

  // Fetch configs
  const { data, isLoading, mutate } = useSWR("llm-configs", () =>
    llmConfigApi.list()
  )
  const configs = data?.data ?? []

  // Dialog state
  const [formOpen, setFormOpen] = useState(false)
  const [editingConfig, setEditingConfig] = useState<LlmConfigVO | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Delete state
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  // Test state
  const [testingId, setTestingId] = useState<string | null>(null)

  /* ---- Create / Edit ---- */
  const handleOpenCreate = useCallback(() => {
    setEditingConfig(null)
    setFormOpen(true)
  }, [])

  const handleOpenEdit = useCallback((config: LlmConfigVO) => {
    setEditingConfig(config)
    setFormOpen(true)
  }, [])

  const handleSubmit = useCallback(
    async (formData: CreateLlmConfigRequest | UpdateLlmConfigRequest) => {
      setSubmitting(true)
      try {
        if (editingConfig) {
          await llmConfigApi.update(editingConfig.configId, formData)
          toast.success("配置已更新")
        } else {
          await llmConfigApi.create(formData as CreateLlmConfigRequest)
          toast.success("配置已创建")
        }
        setFormOpen(false)
        setEditingConfig(null)
        mutate()
      } catch (err) {
        toast.error(
          err instanceof Error ? err.message : "操作失败，请重试"
        )
      } finally {
        setSubmitting(false)
      }
    },
    [editingConfig, mutate]
  )

  /* ---- Delete ---- */
  const handleDelete = useCallback((configId: string) => {
    setDeletingId(configId)
    setDeleteOpen(true)
  }, [])

  const confirmDelete = useCallback(async () => {
    if (!deletingId) return
    try {
      await llmConfigApi.delete(deletingId)
      toast.success("配置已删除")
      mutate()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败")
    } finally {
      setDeleteOpen(false)
      setDeletingId(null)
    }
  }, [deletingId, mutate])

  /* ---- Test connection ---- */
  const handleTest = useCallback(
    async (config: LlmConfigVO) => {
      setTestingId(config.configId)
      try {
        const res = await llmConfigApi.testSavedConfig(config.configId)
        if (res.data) {
          toast.success("连接测试成功")
        } else {
          toast.error("连接测试失败")
        }
      } catch (err) {
        toast.error(
          err instanceof Error ? err.message : "连接测试失败"
        )
      } finally {
        setTestingId(null)
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
            <Settings2 className="w-5 h-5 text-amber-700 dark:text-amber-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50">
              LLM 配置管理
            </h1>
            <p className="text-xs text-stone-400">
              管理大语言模型的连接配置
            </p>
          </div>
        </div>
        <Button
          onClick={handleOpenCreate}
          className="bg-amber-600 hover:bg-amber-700 text-white"
        >
          <Plus className="w-4 h-4 mr-1.5" />
          新建配置
        </Button>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Card key={i}>
              <CardContent className="p-4 space-y-3">
                <Skeleton className="h-5 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
                <div className="space-y-2">
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-2/3" />
                </div>
                <Skeleton className="h-8 w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : configs.length === 0 ? (
        <EmptyState
          icon={<Server className="w-6 h-6 text-stone-400" />}
          title="还没有 LLM 配置"
          description="添加你的第一个大语言模型配置，开始使用 AI 写作功能"
          action={
            <Button
              className="bg-amber-600 hover:bg-amber-700 text-white"
              onClick={handleOpenCreate}
            >
              <Plus className="w-4 h-4 mr-1.5" />
              新建配置
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {configs.map((config) => (
            <LlmConfigCard
              key={config.configId}
              config={config}
              onEdit={() => handleOpenEdit(config)}
              onDelete={() => handleDelete(config.configId)}
              onTest={() => handleTest(config)}
              testing={testingId === config.configId}
            />
          ))}
        </div>
      )}

      {/* Create / Edit dialog */}
      <ConfigFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        editingConfig={editingConfig}
        onSubmit={handleSubmit}
        submitting={submitting}
      />

      {/* Delete confirmation */}
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除配置"
        description="确定要删除这个 LLM 配置吗？此操作不可撤销。"
        onConfirm={confirmDelete}
        confirmText="删除"
        variant="destructive"
      />
    </div>
  )
}
