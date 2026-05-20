"use client"

import Link from "next/link"
import {
  BookOpen,
  PenLine,
  BarChart3,
  Wand2,
  Shield,
  Sparkles,
} from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { buttonVariants } from "@/components/ui/button"
import { useAuth } from "@/hooks/useAuth"
import useSWR from "swr"
import { novelApi } from "@/lib/api/novel"

const features = [
  { icon: Wand2, title: "AI大纲生成", desc: "一键生成百万字长篇大纲" },
  { icon: PenLine, title: "智能创作", desc: "多Agent协作，自动章节生成" },
  { icon: Shield, title: "多层验证", desc: "OOC检测、逻辑检查、节奏分析" },
  { icon: Sparkles, title: "风格卡", desc: "提取写作风格，统一小说基调" },
  { icon: BarChart3, title: "数据面板", desc: "阅读拉力趋势、人物关系图" },
  { icon: BookOpen, title: "向导模式", desc: "5步引导，从零到百万字" },
]

export default function HomePage() {
  useAuth()
  const { data } = useSWR("novels", () => novelApi.list())

  const novelCount = data?.data?.length || 0
  const totalWords =
    data?.data?.reduce((sum, n) => sum + (n.wordCount || 0), 0) || 0
  const totalChapters =
    data?.data?.reduce((sum, n) => sum + (n.chapterCount || 0), 0) || 0

  return (
    <div className="mx-auto max-w-4xl px-6 py-12">
      <div className="mb-12 text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-amber-100 dark:bg-amber-900">
          <PenLine className="h-8 w-8 text-amber-700 dark:text-amber-400" />
        </div>
        <h1 className="mb-2 text-2xl font-bold text-stone-900 dark:text-stone-50">
          欢迎使用 MoYun
        </h1>
        <p className="text-sm text-stone-500">百万字AI长篇小说创作系统</p>
      </div>

      <div className="mb-12 grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-amber-600 dark:text-amber-400">
              {novelCount}
            </div>
            <div className="text-xs text-stone-400">小说数量</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-amber-600 dark:text-amber-400">
              {(totalWords / 10000).toFixed(1)}万
            </div>
            <div className="text-xs text-stone-400">总字数</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-amber-600 dark:text-amber-400">
              {totalChapters}
            </div>
            <div className="text-xs text-stone-400">总章节数</div>
          </CardContent>
        </Card>
      </div>

      <div className="mb-12 grid grid-cols-3 gap-4">
        {features.map((f) => (
          <Card
            key={f.title}
            className="transition-shadow hover:shadow-md"
          >
            <CardContent className="p-4">
              <f.icon className="mb-2 h-5 w-5 text-amber-600 dark:text-amber-400" />
              <h3 className="text-sm font-semibold text-stone-900 dark:text-stone-50">
                {f.title}
              </h3>
              <p className="mt-1 text-xs text-stone-500">{f.desc}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="text-center">
        <Link
          href="/create"
          className={buttonVariants({
            className: "bg-amber-600 hover:bg-amber-700 text-white",
          })}
        >
          开始创作
        </Link>
      </div>
    </div>
  )
}
