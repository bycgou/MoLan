"use client"

import { useState, useCallback, useMemo } from "react"
import { useParams } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, Plus, Filter } from "lucide-react"
import useSWR from "swr"
import { ClueList } from "@/components/novel/ClueList"
import { EmptyState } from "@/components/shared/EmptyState"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { clueApi } from "@/lib/api/clue"
import { useAuth } from "@/hooks/useAuth"
import type { ClueVO, CreateClueRequest } from "@/types"

const CLUE_TYPES = ["主线伏笔", "支线伏笔", "暗线"]

export default function CluesPage() {
  const { id } = useParams<{ id: string }>()
  useAuth()

  const { data: cluesData, isLoading, mutate } = useSWR(
    id ? `clues-${id}` : null,
    () => clueApi.list(id)
  )
  const clues = cluesData?.data ?? []

  // Filter state
  const [typeFilter, setTypeFilter] = useState<string>("all")
  const [statusFilter, setStatusFilter] = useState<string>("all")

  // Create dialog state
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [formTitle, setFormTitle] = useState("")
  const [formDescription, setFormDescription] = useState("")
  const [formClueType, setFormClueType] = useState("主线伏笔")
  const [formPlantedChapter, setFormPlantedChapter] = useState("")
  const [saving, setSaving] = useState(false)

  // Recycle dialog state
  const [recycleDialogOpen, setRecycleDialogOpen] = useState(false)
  const [recycleClueId, setRecycleClueId] = useState<string | null>(null)
  const [recycleChapter, setRecycleChapter] = useState("")
  const [recycling, setRecycling] = useState(false)

  // Filtered clues
  const filteredClues = useMemo(() => {
    return clues.filter((clue: ClueVO) => {
      if (typeFilter !== "all" && clue.clueType !== typeFilter) return false
      if (statusFilter === "未回收" && clue.status !== 0) return false
      if (statusFilter === "已回收" && clue.status !== 1) return false
      return true
    })
  }, [clues, typeFilter, statusFilter])

  const openCreateDialog = useCallback(() => {
    setFormTitle("")
    setFormDescription("")
    setFormClueType("主线伏笔")
    setFormPlantedChapter("")
    setCreateDialogOpen(true)
  }, [])

  const openRecycleDialog = useCallback((clueId: string) => {
    setRecycleClueId(clueId)
    setRecycleChapter("")
    setRecycleDialogOpen(true)
  }, [])

  const handleCreate = useCallback(async () => {
    if (!formTitle.trim() || !formPlantedChapter) return
    setSaving(true)
    try {
      const data: CreateClueRequest = {
        title: formTitle,
        description: formDescription,
        clueType: formClueType,
        plantedChapter: parseInt(formPlantedChapter, 10),
      }
      await clueApi.create(id, data)
      setCreateDialogOpen(false)
      mutate()
    } finally {
      setSaving(false)
    }
  }, [id, formTitle, formDescription, formClueType, formPlantedChapter, mutate])

  const handleRecycle = useCallback(async () => {
    if (!recycleClueId || !recycleChapter) return
    setRecycling(true)
    try {
      await clueApi.recycle(id, recycleClueId, parseInt(recycleChapter, 10))
      setRecycleDialogOpen(false)
      mutate()
    } finally {
      setRecycling(false)
    }
  }, [id, recycleClueId, recycleChapter, mutate])

  if (isLoading) {
    return (
      <div className="p-4 md:p-6 space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="p-4 md:p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Link
            href={`/novel/${id}`}
            className="flex items-center gap-1.5 text-xs text-stone-500 hover:text-stone-700 dark:hover:text-stone-300"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回
          </Link>
          <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50">线索管理</h1>
        </div>
        <Button size="sm" onClick={openCreateDialog}>
          <Plus className="w-3.5 h-3.5 mr-1" />
          添加线索
        </Button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <div className="flex items-center gap-2">
          <Filter className="w-3.5 h-3.5 text-stone-400" />
          <span className="text-xs text-stone-500">筛选：</span>
        </div>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="flex h-8 rounded-lg border border-input bg-transparent px-2.5 py-1 text-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          <option value="all">全部类型</option>
          {CLUE_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="flex h-8 rounded-lg border border-input bg-transparent px-2.5 py-1 text-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          <option value="all">全部状态</option>
          <option value="未回收">未回收</option>
          <option value="已回收">已回收</option>
        </select>
      </div>

      {/* Clue list */}
      {filteredClues.length === 0 && clues.length === 0 ? (
        <EmptyState
          icon={<Plus className="w-6 h-6 text-stone-400" />}
          title="暂无线索"
          description="点击右上角的「添加线索」按钮创建第一条线索"
          action={
            <Button size="sm" onClick={openCreateDialog}>
              <Plus className="w-3.5 h-3.5 mr-1" />
              添加线索
            </Button>
          }
        />
      ) : (
        <ClueList clues={filteredClues} onRecycle={openRecycleDialog} />
      )}

      {/* Create Clue Dialog */}
      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>添加线索</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="clue-title">标题</Label>
              <Input
                id="clue-title"
                value={formTitle}
                onChange={(e) => setFormTitle(e.target.value)}
                placeholder="线索标题"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="clue-type">类型</Label>
              <select
                id="clue-type"
                value={formClueType}
                onChange={(e) => setFormClueType(e.target.value)}
                className="flex h-8 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
              >
                {CLUE_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="clue-planted">埋设章节</Label>
              <Input
                id="clue-planted"
                type="number"
                value={formPlantedChapter}
                onChange={(e) => setFormPlantedChapter(e.target.value)}
                placeholder="章节号"
                min={1}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="clue-desc">描述</Label>
              <Textarea
                id="clue-desc"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder="线索描述"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setCreateDialogOpen(false)}>
              取消
            </Button>
            <Button size="sm" onClick={handleCreate} disabled={saving || !formTitle.trim() || !formPlantedChapter}>
              {saving ? "创建中..." : "创建"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Recycle Clue Dialog */}
      <Dialog open={recycleDialogOpen} onOpenChange={setRecycleDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>回收线索</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-stone-500">
              指定线索回收的章节号，标记该线索在哪个章节被揭示或回收。
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="recycle-chapter">回收章节</Label>
              <Input
                id="recycle-chapter"
                type="number"
                value={recycleChapter}
                onChange={(e) => setRecycleChapter(e.target.value)}
                placeholder="章节号"
                min={1}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setRecycleDialogOpen(false)}>
              取消
            </Button>
            <Button size="sm" onClick={handleRecycle} disabled={recycling || !recycleChapter}>
              {recycling ? "回收中..." : "确认回收"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
