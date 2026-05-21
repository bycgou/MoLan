"use client"

import { useState, useCallback } from "react"
import { useParams } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, UserCircle, Plus } from "lucide-react"
import useSWR from "swr"
import { CharacterCard } from "@/components/novel/CharacterCard"
import { EmptyState } from "@/components/shared/EmptyState"
import { Button, buttonVariants } from "@/components/ui/button"
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
import { characterApi } from "@/lib/api/character"
import { useAuth } from "@/hooks/useAuth"
import { cn } from "@/lib/utils"
import type {
  CharacterVO,
  CreateCharacterRequest,
  UpdateCharacterRequest,
  AddGrowthNodeRequest,
} from "@/types"

const ROLES = ["主角", "配角", "反派", "龙套"]
const GROWTH_TYPES = ["能力提升", "性格转变", "关系变化"]

export default function CharactersPage() {
  const { id } = useParams<{ id: string }>()
  useAuth()

  const { data: charactersData, isLoading, mutate } = useSWR(
    id ? `characters-${id}` : null,
    () => characterApi.list(id)
  )
  const characters = charactersData?.data ?? []

  // Create / Edit dialog state
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingCharacter, setEditingCharacter] = useState<CharacterVO | null>(null)
  const [formName, setFormName] = useState("")
  const [formRole, setFormRole] = useState("主角")
  const [formDescription, setFormDescription] = useState("")
  const [formPersonality, setFormPersonality] = useState("")
  const [formBackground, setFormBackground] = useState("")
  const [saving, setSaving] = useState(false)

  // Growth node dialog state
  const [growthDialogOpen, setGrowthDialogOpen] = useState(false)
  const [growthCharacterId, setGrowthCharacterId] = useState<string | null>(null)
  const [growthChapterNum, setGrowthChapterNum] = useState("")
  const [growthTitle, setGrowthTitle] = useState("")
  const [growthDescription, setGrowthDescription] = useState("")
  const [growthType, setGrowthType] = useState("能力提升")
  const [savingGrowth, setSavingGrowth] = useState(false)

  const openCreateDialog = useCallback(() => {
    setEditingCharacter(null)
    setFormName("")
    setFormRole("主角")
    setFormDescription("")
    setFormPersonality("")
    setFormBackground("")
    setDialogOpen(true)
  }, [])

  const openEditDialog = useCallback((character: CharacterVO) => {
    setEditingCharacter(character)
    setFormName(character.name)
    setFormRole(character.role)
    setFormDescription(character.description)
    setFormPersonality(character.personality)
    setFormBackground(character.background)
    setDialogOpen(true)
  }, [])

  const openGrowthDialog = useCallback((characterId: string) => {
    setGrowthCharacterId(characterId)
    setGrowthChapterNum("")
    setGrowthTitle("")
    setGrowthDescription("")
    setGrowthType("能力提升")
    setGrowthDialogOpen(true)
  }, [])

  const handleSaveCharacter = useCallback(async () => {
    if (!formName.trim()) return
    setSaving(true)
    try {
      if (editingCharacter) {
        const data: UpdateCharacterRequest = {
          name: formName,
          role: formRole,
          description: formDescription,
          personality: formPersonality,
          background: formBackground,
        }
        await characterApi.update(id, editingCharacter.characterId, data)
      } else {
        const data: CreateCharacterRequest = {
          name: formName,
          role: formRole,
          description: formDescription,
          personality: formPersonality,
          background: formBackground,
        }
        await characterApi.create(id, data)
      }
      setDialogOpen(false)
      mutate()
    } finally {
      setSaving(false)
    }
  }, [id, editingCharacter, formName, formRole, formDescription, formPersonality, formBackground, mutate])

  const handleSaveGrowth = useCallback(async () => {
    if (!growthCharacterId || !growthTitle.trim() || !growthChapterNum) return
    setSavingGrowth(true)
    try {
      const data: AddGrowthNodeRequest = {
        chapterNum: parseInt(growthChapterNum, 10),
        title: growthTitle,
        description: growthDescription,
        growthType,
      }
      await characterApi.addGrowthNode(id, growthCharacterId, data)
      setGrowthDialogOpen(false)
      mutate()
    } finally {
      setSavingGrowth(false)
    }
  }, [id, growthCharacterId, growthChapterNum, growthTitle, growthDescription, growthType, mutate])

  if (isLoading) {
    return (
      <div className="p-4 md:p-6 space-y-4">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
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
          <h1 className="text-lg font-bold text-stone-900 dark:text-stone-50">人物管理</h1>
        </div>
        <Button size="sm" onClick={openCreateDialog}>
          <Plus className="w-3.5 h-3.5 mr-1" />
          添加人物
        </Button>
      </div>

      {/* Character grid */}
      {characters.length === 0 ? (
        <EmptyState
          icon={<UserCircle className="w-6 h-6 text-stone-400" />}
          title="暂无人物"
          description="点击右上角的「添加人物」按钮创建第一个人物"
          action={
            <Button size="sm" onClick={openCreateDialog}>
              <Plus className="w-3.5 h-3.5 mr-1" />
              添加人物
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {characters.map((character) => (
            <CharacterCard
              key={character.characterId}
              character={character}
              onEdit={() => openEditDialog(character)}
              onAddGrowth={() => openGrowthDialog(character.characterId)}
            />
          ))}
        </div>
      )}

      {/* Create / Edit Character Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingCharacter ? "编辑人物" : "添加人物"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="char-name">名称</Label>
              <Input
                id="char-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="人物名称"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="char-role">角色</Label>
              <select
                id="char-role"
                value={formRole}
                onChange={(e) => setFormRole(e.target.value)}
                className="flex h-8 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="char-desc">描述</Label>
              <Textarea
                id="char-desc"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder="人物描述"
                rows={2}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="char-personality">性格</Label>
              <Textarea
                id="char-personality"
                value={formPersonality}
                onChange={(e) => setFormPersonality(e.target.value)}
                placeholder="人物性格"
                rows={2}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="char-bg">背景</Label>
              <Textarea
                id="char-bg"
                value={formBackground}
                onChange={(e) => setFormBackground(e.target.value)}
                placeholder="人物背景"
                rows={2}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button size="sm" onClick={handleSaveCharacter} disabled={saving || !formName.trim()}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Growth Node Dialog */}
      <Dialog open={growthDialogOpen} onOpenChange={setGrowthDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>添加成长节点</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="growth-chapter">章节号</Label>
              <Input
                id="growth-chapter"
                type="number"
                value={growthChapterNum}
                onChange={(e) => setGrowthChapterNum(e.target.value)}
                placeholder="章节号"
                min={1}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="growth-title">标题</Label>
              <Input
                id="growth-title"
                value={growthTitle}
                onChange={(e) => setGrowthTitle(e.target.value)}
                placeholder="成长节点标题"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="growth-type">成长类型</Label>
              <select
                id="growth-type"
                value={growthType}
                onChange={(e) => setGrowthType(e.target.value)}
                className="flex h-8 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
              >
                {GROWTH_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="growth-desc">描述</Label>
              <Textarea
                id="growth-desc"
                value={growthDescription}
                onChange={(e) => setGrowthDescription(e.target.value)}
                placeholder="成长描述"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setGrowthDialogOpen(false)}>
              取消
            </Button>
            <Button
              size="sm"
              onClick={handleSaveGrowth}
              disabled={savingGrowth || !growthTitle.trim() || !growthChapterNum}
            >
              {savingGrowth ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
