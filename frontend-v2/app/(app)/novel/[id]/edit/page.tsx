"use client"

import { useEffect } from "react"
import { useParams, useRouter } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, Loader2 } from "lucide-react"
import useSWR from "swr"
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
} from "@/components/ui/card"
import { novelApi } from "@/lib/api/novel"
import { useAuth } from "@/hooks/useAuth"
import type { UpdateNovelRequest } from "@/types"

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

const statusOptions = [
  { value: 0, label: "草稿" },
  { value: 1, label: "连载中" },
  { value: 2, label: "已完结" },
]

const novelEditSchema = z.object({
  novelName: z.string().min(1, "小说名称不能为空"),
  genre: z.string().min(1, "请选择类型"),
  status: z.number(),
  totalOutline: z.string(),
  worldSetting: z.string(),
})

type NovelEditForm = z.infer<typeof novelEditSchema>

export default function NovelEditPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  useAuth()

  const { data: novelData, isLoading } = useSWR(
    id ? `novel-${id}` : null,
    () => novelApi.get(id)
  )
  const novel = novelData?.data ?? null

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<NovelEditForm>({
    resolver: zodResolver(novelEditSchema),
    defaultValues: {
      novelName: "",
      genre: "",
      status: 0,
      totalOutline: "",
      worldSetting: "",
    },
  })

  // Pre-fill form when novel data loads
  useEffect(() => {
    if (novel) {
      reset({
        novelName: novel.novelName,
        genre: novel.genre,
        status: novel.status,
        totalOutline: novel.totalOutline,
        worldSetting: novel.worldSetting,
      })
    }
  }, [novel, reset])

  const onSubmit = async (data: NovelEditForm) => {
    const payload: UpdateNovelRequest = {
      novelName: data.novelName,
      genre: data.genre,
      status: data.status,
      totalOutline: data.totalOutline,
      worldSetting: data.worldSetting,
    }
    await novelApi.update(id, payload)
    router.push(`/novel/${id}`)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="w-6 h-6 animate-spin text-stone-400" />
      </div>
    )
  }

  if (!novel) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-stone-500">小说不存在</p>
        <Link href="/novels">
          <Button variant="outline">返回小说列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto p-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <Link
          href={`/novel/${id}`}
          className="flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-700 dark:hover:text-stone-300"
        >
          <ArrowLeft className="w-4 h-4" />
          返回
        </Link>
        <h1 className="text-xl font-bold text-stone-900 dark:text-stone-50">
          编辑小说
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>小说信息</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            {/* Novel Name */}
            <div className="space-y-1.5">
              <Label htmlFor="novelName">小说名称</Label>
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
              <Label htmlFor="genre">类型</Label>
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

            {/* Status */}
            <div className="space-y-1.5">
              <Label htmlFor="status">状态</Label>
              <select
                id="status"
                {...register("status", { valueAsNumber: true })}
                className="w-full h-8 px-3 py-1 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 text-sm focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none"
              >
                {statusOptions.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </div>

            {/* Total Outline */}
            <div className="space-y-1.5">
              <Label htmlFor="totalOutline">总纲</Label>
              <Textarea
                id="totalOutline"
                {...register("totalOutline")}
                placeholder="请输入小说总纲"
                rows={6}
              />
            </div>

            {/* World Setting */}
            <div className="space-y-1.5">
              <Label htmlFor="worldSetting">世界观设定</Label>
              <Textarea
                id="worldSetting"
                {...register("worldSetting")}
                placeholder="请输入世界观设定"
                rows={6}
              />
            </div>

            {/* Actions */}
            <div className="flex gap-3 pt-2">
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && (
                  <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                )}
                保存
              </Button>
              <Link href={`/novel/${id}`}>
                <Button type="button" variant="outline">
                  取消
                </Button>
              </Link>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
