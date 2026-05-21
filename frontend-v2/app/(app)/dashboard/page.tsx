"use client"

import { useState, useMemo } from "react"
import useSWR from "swr"
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts"
import {
  BookOpen,
  FileText,
  Users,
  TrendingUp,
  BarChart3,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
} from "lucide-react"
import { useAuth } from "@/hooks/useAuth"
import { EmptyState } from "@/components/shared/EmptyState"
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Badge } from "@/components/ui/badge"
import { novelApi } from "@/lib/api/novel"
import { chapterApi } from "@/lib/api/chapter"
import { characterApi } from "@/lib/api/character"
import { rhythmApi } from "@/lib/api/rhythm"
import { hookApi } from "@/lib/api/hook"
import type { ChapterListItem } from "@/types"

/* ------------------------------------------------------------------ */
/* Trend badge config                                                 */
/* ------------------------------------------------------------------ */

const trendConfig: Record<
  string,
  { label: string; icon: typeof ArrowUpRight; className: string }
> = {
  rising: {
    label: "上升",
    icon: ArrowUpRight,
    className:
      "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  },
  stable: {
    label: "稳定",
    icon: Minus,
    className:
      "bg-stone-100 text-stone-600 dark:bg-stone-700 dark:text-stone-300",
  },
  declining: {
    label: "下降",
    icon: ArrowDownRight,
    className:
      "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
  },
}

/* ------------------------------------------------------------------ */
/* Small helper components                                            */
/* ------------------------------------------------------------------ */

function HookScoreCell({ score }: { score?: number }) {
  if (score === undefined || score === null)
    return <span className="text-stone-400">--</span>
  const color =
    score >= 7
      ? "text-green-600 dark:text-green-400"
      : score >= 4
        ? "text-amber-600 dark:text-amber-400"
        : "text-red-600 dark:text-red-400"
  return <span className={`font-medium ${color}`}>{score.toFixed(1)}</span>
}

function PullLevelBadge({ level }: { level?: string }) {
  if (!level) return <span className="text-stone-400">--</span>
  const cls =
    level === "高"
      ? "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300"
      : level === "中"
        ? "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300"
        : "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300"
  return (
    <Badge variant="secondary" className={cls}>
      {level}
    </Badge>
  )
}

/* ------------------------------------------------------------------ */
/* ChapterRow — fetches its own hook analysis via useSWR              */
/* ------------------------------------------------------------------ */

function ChapterRow({
  ch,
  novelId,
}: {
  ch: ChapterListItem
  novelId: string
}) {
  const { data: hookRes } = useSWR(
    novelId ? ["dashboard-hook", novelId, ch.chapterNum] : null,
    () => hookApi.analyzeHook(novelId, ch.chapterNum)
  )
  const hook = hookRes?.data

  return (
    <tr className="border-b border-stone-100 dark:border-stone-800 last:border-0 hover:bg-stone-50 dark:hover:bg-stone-800/50 transition-colors">
      <td className="py-2.5 text-stone-900 dark:text-stone-100 font-medium">
        第{ch.chapterNum}章
      </td>
      <td className="py-2.5 text-stone-700 dark:text-stone-300 max-w-[200px] truncate">
        {ch.chapterTitle}
      </td>
      <td className="py-2.5 text-right text-stone-500">
        {ch.wordCount?.toLocaleString() ?? "--"}
      </td>
      <td className="py-2.5 text-center">
        <HookScoreCell score={hook?.openingAttraction} />
      </td>
      <td className="py-2.5 text-center">
        <HookScoreCell score={hook?.suspenseSetup} />
      </td>
      <td className="py-2.5 text-center">
        <HookScoreCell score={hook?.endingHook} />
      </td>
      <td className="py-2.5 text-center">
        <HookScoreCell score={hook?.totalScore} />
      </td>
      <td className="py-2.5 text-center">
        <PullLevelBadge level={hook?.pullLevel} />
      </td>
    </tr>
  )
}

/* ------------------------------------------------------------------ */
/* Dashboard page                                                     */
/* ------------------------------------------------------------------ */

export default function DashboardPage() {
  useAuth()

  // Fetch novels for selector
  const { data: novelsRes, isLoading: novelsLoading } = useSWR(
    "dashboard-novels",
    () => novelApi.list()
  )
  const novels = novelsRes?.data ?? []

  // Selected novel state
  const [selectedNovelId, setSelectedNovelId] = useState<string>("")

  // Auto-select first novel
  const activeNovelId = selectedNovelId || novels[0]?.novelId || ""

  // Fetch chapters
  const { data: chaptersRes, isLoading: chaptersLoading } = useSWR(
    activeNovelId ? ["dashboard-chapters", activeNovelId] : null,
    () => chapterApi.list(activeNovelId)
  )
  const chapters = chaptersRes?.data ?? []

  // Fetch characters
  const { data: charactersRes } = useSWR(
    activeNovelId ? ["dashboard-characters", activeNovelId] : null,
    () => characterApi.list(activeNovelId)
  )
  const characters = charactersRes?.data ?? []

  // Fetch read-pull trend
  const { data: trendRes, isLoading: trendLoading } = useSWR(
    activeNovelId ? ["dashboard-trend", activeNovelId] : null,
    () => rhythmApi.getReadPullTrend(activeNovelId)
  )
  const trendData = trendRes?.data

  // Chart data
  const chartData = useMemo(() => {
    if (!trendData) return []
    return (
      trendData.chapterNumbers?.map((ch, i) => ({
        chapter: `${ch}`,
        score: trendData.readPullScores?.[i] ?? trendData.scores?.[i] ?? 0,
      })) ??
      trendData.scores?.map((s, i) => ({
        chapter: `${i + 1}`,
        score: s,
      })) ??
      []
    )
  }, [trendData])

  // Stats
  const totalChapters = chapters.length
  const totalWords = chapters.reduce((sum, ch) => sum + (ch.wordCount || 0), 0)
  const characterCount = characters.length
  const avgScore = trendData?.averageScore ?? 0

  // Recent chapters (last 10, sorted newest first)
  const recentChapters = useMemo(
    () =>
      [...chapters]
        .sort((a, b) => b.chapterNum - a.chapterNum)
        .slice(0, 10),
    [chapters]
  )

  // Loading state
  if (novelsLoading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-80" />
        <Skeleton className="h-64" />
      </div>
    )
  }

  // Empty state
  if (novels.length === 0) {
    return (
      <div className="flex items-center justify-center h-full">
        <EmptyState
          icon={<BarChart3 className="w-6 h-6 text-stone-400" />}
          title="还没有小说"
          description="创建一部小说后即可查看数据分析"
        />
      </div>
    )
  }

  const trend = trendData?.trend
    ? trendConfig[trendData.trend] || trendConfig.stable
    : null

  return (
    <div className="p-4 md:p-6 space-y-4 md:space-y-6 max-w-6xl mx-auto">
      {/* Header with novel selector */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <BarChart3 className="w-5 h-5 text-amber-600" />
          <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50">
            数据分析
          </h1>
        </div>
        <select
          value={activeNovelId}
          onChange={(e) => setSelectedNovelId(e.target.value)}
          className="h-9 rounded-lg border border-stone-200 dark:border-stone-700 bg-white dark:bg-stone-800 px-3 text-sm text-stone-900 dark:text-stone-100 focus:outline-none focus:ring-2 focus:ring-amber-500/40"
        >
          {novels.map((n) => (
            <option key={n.novelId} value={n.novelId}>
              {n.novelName}
            </option>
          ))}
        </select>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 dark:bg-amber-900/40">
              <FileText className="w-5 h-5 text-amber-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-stone-900 dark:text-stone-50">
                {totalChapters}
              </div>
              <div className="text-xs text-stone-400">总章节</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/40">
              <BookOpen className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-stone-900 dark:text-stone-50">
                {totalWords >= 10000
                  ? `${(totalWords / 10000).toFixed(1)}万`
                  : totalWords.toLocaleString()}
              </div>
              <div className="text-xs text-stone-400">总字数</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/40">
              <Users className="w-5 h-5 text-purple-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-stone-900 dark:text-stone-50">
                {characterCount}
              </div>
              <div className="text-xs text-stone-400">人物数</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/40">
              <TrendingUp className="w-5 h-5 text-green-600" />
            </div>
            <div>
              <div className="text-2xl font-bold text-stone-900 dark:text-stone-50">
                {avgScore > 0 ? avgScore.toFixed(1) : "--"}
              </div>
              <div className="text-xs text-stone-400">平均拉力分</div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Read-pull trend chart */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">阅读拉力趋势</CardTitle>
            {trend && (
              <Badge variant="secondary" className={trend.className}>
                <trend.icon className="w-3 h-3 mr-1" />
                {trend.label}
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {trendLoading ? (
            <Skeleton className="h-64 w-full" />
          ) : chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={chartData}>
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="currentColor"
                  className="text-stone-200 dark:text-stone-700"
                />
                <XAxis
                  dataKey="chapter"
                  tick={{ fontSize: 11 }}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(v) => `${v}章`}
                  className="text-stone-400"
                />
                <YAxis
                  domain={[0, 10]}
                  tick={{ fontSize: 11 }}
                  tickLine={false}
                  axisLine={false}
                  className="text-stone-400"
                />
                <Tooltip
                  contentStyle={{
                    borderRadius: 8,
                    border: "1px solid var(--color-stone-200)",
                    fontSize: 12,
                  }}
                  formatter={(value) => [Number(value).toFixed(1), "拉力分"]}
                  labelFormatter={(label) => `第${label}章`}
                />
                <Line
                  type="monotone"
                  dataKey="score"
                  stroke="#d97706"
                  strokeWidth={2}
                  dot={{ r: 3, fill: "#d97706" }}
                  activeDot={{ r: 5, fill: "#d97706" }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-48 text-stone-400 text-sm">
              暂无趋势数据
            </div>
          )}
        </CardContent>
      </Card>

      {/* Recent chapters table */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">近期章节</CardTitle>
        </CardHeader>
        <CardContent>
          {chaptersLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : recentChapters.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-stone-200 dark:border-stone-700">
                    <th className="pb-2 text-left font-medium text-stone-500">
                      章节
                    </th>
                    <th className="pb-2 text-left font-medium text-stone-500">
                      标题
                    </th>
                    <th className="pb-2 text-right font-medium text-stone-500">
                      字数
                    </th>
                    <th className="pb-2 text-center font-medium text-stone-500">
                      开篇吸引
                    </th>
                    <th className="pb-2 text-center font-medium text-stone-500">
                      悬念设置
                    </th>
                    <th className="pb-2 text-center font-medium text-stone-500">
                      结尾钩子
                    </th>
                    <th className="pb-2 text-center font-medium text-stone-500">
                      总分
                    </th>
                    <th className="pb-2 text-center font-medium text-stone-500">
                      拉力等级
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {recentChapters.map((ch) => (
                    <ChapterRow
                      key={ch.chapterId}
                      ch={ch}
                      novelId={activeNovelId}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="flex items-center justify-center h-24 text-stone-400 text-sm">
              暂无章节数据
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
