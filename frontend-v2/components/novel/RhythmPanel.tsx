"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { RhythmStatusVO } from "@/types"

const strandInfo: Record<string, { label: string; color: string }> = {
  quest: { label: "主线", color: "text-amber-600" },
  fire: { label: "情感", color: "text-rose-600" },
  constellation: { label: "世界", color: "text-blue-600" },
}

interface RhythmPanelProps {
  rhythm: RhythmStatusVO | null
  recommendation?: string
}

export function RhythmPanel({ rhythm, recommendation }: RhythmPanelProps) {
  if (!rhythm) return null

  return (
    <Card className="border-stone-200 dark:border-stone-700">
      <CardHeader className="pb-2">
        <CardTitle className="text-xs font-semibold">节奏面板</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="flex items-center justify-between text-xs">
          <span className="text-stone-500">当前线索</span>
          <span className={strandInfo[rhythm.lastStrand]?.color || ""}>
            {strandInfo[rhythm.lastStrand]?.label || rhythm.lastStrand}
          </span>
        </div>
        <div className="grid grid-cols-3 gap-2 text-center">
          {(["quest", "fire", "constellation"] as const).map(type => (
            <div key={type} className="p-2 rounded-md bg-stone-50 dark:bg-stone-700">
              <div className={`text-sm font-bold ${strandInfo[type].color}`}>
                {((type === "quest" ? rhythm.questRatio : type === "fire" ? rhythm.fireRatio : rhythm.constellationRatio) * 100).toFixed(0)}%
              </div>
              <div className="text-[10px] text-stone-400">{strandInfo[type].label}</div>
            </div>
          ))}
        </div>
        {recommendation && (
          <div className="text-xs text-stone-500 bg-amber-50 dark:bg-amber-950 p-2 rounded-md">
            建议：{recommendation}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
