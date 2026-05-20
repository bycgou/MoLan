import { cn } from "@/lib/utils"
import { Card, CardContent } from "@/components/ui/card"

interface StatsCardProps {
  label: string
  value: string | number
  className?: string
}

export function StatsCard({ label, value, className }: StatsCardProps) {
  return (
    <Card className={cn("border-stone-200 dark:border-stone-700", className)}>
      <CardContent className="p-3 text-center">
        <div className="text-xl font-bold text-amber-600 dark:text-amber-400">{value}</div>
        <div className="text-[10px] text-stone-400 mt-0.5">{label}</div>
      </CardContent>
    </Card>
  )
}
