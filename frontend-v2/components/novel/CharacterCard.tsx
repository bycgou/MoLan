"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Edit, Plus } from "lucide-react"
import type { CharacterVO } from "@/types"

const roleColors: Record<string, string> = {
  "主角": "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  "配角": "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  "反派": "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  "龙套": "bg-stone-100 text-stone-600 dark:bg-stone-700 dark:text-stone-300",
}

interface CharacterCardProps {
  character: CharacterVO
  onEdit?: () => void
  onAddGrowth?: () => void
}

export function CharacterCard({ character, onEdit, onAddGrowth }: CharacterCardProps) {
  return (
    <Card className="border-stone-200 dark:border-stone-700">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CardTitle className="text-sm">{character.name}</CardTitle>
            <Badge variant="secondary" className={roleColors[character.role] || ""}>
              {character.role}
            </Badge>
          </div>
          <Button variant="ghost" size="icon-sm" onClick={onEdit}>
            <Edit className="w-3.5 h-3.5" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {character.description && (
          <div>
            <div className="text-[10px] text-stone-400 mb-0.5">描述</div>
            <div className="text-xs text-stone-600 dark:text-stone-300">{character.description}</div>
          </div>
        )}
        {character.personality && (
          <div>
            <div className="text-[10px] text-stone-400 mb-0.5">性格</div>
            <div className="text-xs text-stone-600 dark:text-stone-300">{character.personality}</div>
          </div>
        )}
        {character.background && (
          <div>
            <div className="text-[10px] text-stone-400 mb-0.5">背景</div>
            <div className="text-xs text-stone-600 dark:text-stone-300">{character.background}</div>
          </div>
        )}

        {character.growthNodes && character.growthNodes.length > 0 && (
          <div>
            <div className="text-[10px] text-stone-400 mb-2">成长时间线</div>
            <div className="space-y-2">
              {character.growthNodes.map((node) => (
                <div key={node.nodeId} className="flex gap-2 text-xs">
                  <div className="w-1 bg-amber-300 dark:bg-amber-700 rounded-full shrink-0" />
                  <div>
                    <span className="text-stone-400">第{node.chapterNum}章</span>
                    <span className="font-medium text-stone-900 dark:text-stone-50 ml-2">{node.title}</span>
                    <span className="text-[10px] text-stone-400 ml-2">{node.growthType}</span>
                    <div className="text-stone-500 mt-0.5">{node.description}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <Button variant="outline" size="sm" className="w-full" onClick={onAddGrowth}>
          <Plus className="w-3 h-3 mr-1" />添加成长节点
        </Button>
      </CardContent>
    </Card>
  )
}
