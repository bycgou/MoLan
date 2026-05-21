"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, Loader2, Sparkles, Info } from "lucide-react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { zodResolver } from "@hookform/resolvers/zod"
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
import { novelApi } from "@/lib/api/novel"
import { genreTemplateApi } from "@/lib/api/genre-template"
import { useAuth } from "@/hooks/useAuth"
import type { GenreTemplateVO } from "@/types"

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

const novelCreateSchema = z.object({
  novelName: z.string().min(1, "小说名称不能为空"),
  genre: z.string().min(1, "请选择类型"),
  novelType: z.string(),
  tone: z.string(),
  tabooList: z.string(),
  totalOutline: z.string(),
  worldSetting: z.string(),
})

type NovelCreateForm = z.infer<typeof novelCreateSchema>

/** Convert comma-separated tags to JSON array string */
function tagsToJson(value: string): string {
  const tags = value
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
  return JSON.stringify(tags)
}

export default function CreateNovelPage() {
  const router = useRouter()
  useAuth()

  const [generating, setGenerating] = useState(false)
  const [generateError, setGenerateError] = useState<string | null>(null)
  const [templateInfo, setTemplateInfo] = useState<GenreTemplateVO | null>(null)
  const [templateLoading, setTemplateLoading] = useState(false)

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<NovelCreateForm>({
    resolver: zodResolver(novelCreateSchema),
    defaultValues: {
      novelName: "",
      genre: "",
      novelType: "",
      tone: "",
      tabooList: "",
      totalOutline: "",
      worldSetting: "",
    },
  })

  const currentGenre = watch("genre")

  // Fetch genre template when genre changes
  useEffect(() => {
    if (!currentGenre) {
      setTemplateInfo(null)
      return
    }

    let cancelled = false
    setTemplateLoading(true)
    genreTemplateApi
      .getByGenre(currentGenre)
      .then((res) => {
        if (cancelled) return
        const templates = res.data
        setTemplateInfo(templates && templates.length > 0 ? templates[0] : null)
      })
      .catch(() => {
        if (!cancelled) setTemplateInfo(null)
      })
      .finally(() => {
        if (!cancelled) setTemplateLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [currentGenre])

  const handleAiGenerate = async () => {
    const novelName = watch("novelName")
    const genre = watch("genre")

    if (!novelName.trim()) {
      setGenerateError("请先填写小说名称")
      return
    }
    if (!genre) {
      setGenerateError("请先选择类型")
      return
    }

    setGenerating(true)
    setGenerateError(null)
    try {
      const res = await novelApi.autoGenerateOutline({ novelName, genre })
      const data = res.data as Record<string, unknown>
      if (data.totalOutline) {
        setValue("totalOutline", String(data.totalOutline))
      } else if (data.outline) {
        setValue("totalOutline", String(data.outline))
      }
      if (data.worldSetting) {
        setValue("worldSetting", String(data.worldSetting))
      }
    } catch (err) {
      setGenerateError(
        err instanceof Error ? err.message : "AI生成失败，请重试"
      )
    } finally {
      setGenerating(false)
    }
  }

  const onSubmit = async (data: NovelCreateForm) => {
    const payload = {
      novelName: data.novelName,
      genre: data.genre,
      novelType: data.novelType ? tagsToJson(data.novelType) : undefined,
      tone: data.tone ? tagsToJson(data.tone) : undefined,
      tabooList: data.tabooList ? tagsToJson(data.tabooList) : undefined,
      totalOutline: data.totalOutline,
      worldSetting: data.worldSetting,
    }
    const res = await novelApi.create(payload)
    const novelId = res.data.novelId
    router.push(`/novel/${novelId}`)
  }

  return (
    <div className="max-w-[720px] mx-auto p-4 md:p-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <Link
          href="/novels"
          className="flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-700 dark:hover:text-stone-300"
        >
          <ArrowLeft className="w-4 h-4" />
          返回
        </Link>
        <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50">
          创建小说
        </h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Basic Info Card */}
        <Card>
          <CardHeader>
            <CardTitle>基本信息</CardTitle>
            <CardDescription>填写小说的基本信息</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* Novel Name */}
            <div className="space-y-1.5">
              <Label htmlFor="novelName">小说名称 *</Label>
              <Input
                id="novelName"
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
              <Label htmlFor="genre">类型 *</Label>
              <select
                id="genre"
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
                <p className="text-xs text-red-500">{errors.genre.message}</p>
              )}
            </div>

            {/* Genre Template Guidance */}
            {templateLoading && (
              <div className="flex items-center gap-2 text-sm text-stone-400">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                加载题材模板...
              </div>
            )}
            {templateInfo && (
              <div className="rounded-lg border border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950/30 p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm font-medium text-amber-800 dark:text-amber-200">
                  <Info className="w-4 h-4" />
                  {templateInfo.name} 题材参考
                </div>
                {templateInfo.tropes && templateInfo.tropes.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-stone-600 dark:text-stone-300 mb-1">
                      常见套路
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {templateInfo.tropes.map((t) => (
                        <span
                          key={t}
                          className="inline-block text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
                {templateInfo.pitfalls && templateInfo.pitfalls.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-stone-600 dark:text-stone-300 mb-1">
                      常见坑点
                    </p>
                    <ul className="text-xs text-stone-500 dark:text-stone-400 space-y-0.5 list-disc list-inside">
                      {templateInfo.pitfalls.map((p) => (
                        <li key={p}>{p}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {templateInfo.characterArchetypes &&
                  Object.keys(templateInfo.characterArchetypes).length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-stone-600 dark:text-stone-300 mb-1">
                        人物原型
                      </p>
                      <div className="text-xs text-stone-500 dark:text-stone-400 space-y-0.5">
                        {Object.entries(templateInfo.characterArchetypes).map(
                          ([role, desc]) => (
                            <div key={role}>
                              <span className="font-medium">{role}：</span>
                              {desc}
                            </div>
                          )
                        )}
                      </div>
                    </div>
                  )}
              </div>
            )}

            {/* Novel Type (tag input) */}
            <div className="space-y-1.5">
              <Label htmlFor="novelType">故事类型标签</Label>
              <Input
                id="novelType"
                {...register("novelType")}
                placeholder="用逗号分隔，如：升级流, 系统流, 种田"
              />
              <p className="text-xs text-stone-400">
                多个标签用逗号分隔
              </p>
            </div>

            {/* Tone (tag input) */}
            <div className="space-y-1.5">
              <Label htmlFor="tone">基调标签</Label>
              <Input
                id="tone"
                {...register("tone")}
                placeholder="用逗号分隔，如：热血, 轻松, 暗黑"
              />
              <p className="text-xs text-stone-400">
                多个标签用逗号分隔
              </p>
            </div>

            {/* Taboo List (tag input) */}
            <div className="space-y-1.5">
              <Label htmlFor="tabooList">禁忌事项</Label>
              <Input
                id="tabooList"
                {...register("tabooList")}
                placeholder="用逗号分隔，如：不写后宫, 不写悲剧结局"
              />
              <p className="text-xs text-stone-400">
                多个禁忌用逗号分隔
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Outline Card */}
        <Card>
          <CardHeader>
            <CardTitle>大纲与设定</CardTitle>
            <CardDescription>
              编写小说大纲和世界观设定，或使用AI一键生成
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* AI Generate Button */}
            <div className="flex items-center gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={handleAiGenerate}
                disabled={generating}
              >
                {generating ? (
                  <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                ) : (
                  <Sparkles className="w-4 h-4 mr-1.5" />
                )}
                AI一键生成大纲
              </Button>
              {generating && (
                <span className="text-sm text-stone-400">
                  AI正在生成中，请稍候...
                </span>
              )}
            </div>
            {generateError && (
              <p className="text-xs text-red-500">{generateError}</p>
            )}

            {/* Total Outline */}
            <div className="space-y-1.5">
              <Label htmlFor="totalOutline">总纲</Label>
              <Textarea
                id="totalOutline"
                {...register("totalOutline")}
                placeholder="请输入小说总纲，描述故事主线、主要冲突和结局走向"
                rows={8}
              />
            </div>

            {/* World Setting */}
            <div className="space-y-1.5">
              <Label htmlFor="worldSetting">世界观设定</Label>
              <Textarea
                id="worldSetting"
                {...register("worldSetting")}
                placeholder="请输入世界观设定，描述故事发生的背景、规则体系等"
                rows={8}
              />
            </div>
          </CardContent>
        </Card>

        {/* Actions */}
        <div className="flex gap-3">
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && (
              <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
            )}
            创建小说
          </Button>
          <Link href="/novels">
            <Button type="button" variant="outline">
              取消
            </Button>
          </Link>
        </div>
      </form>
    </div>
  )
}
