import { Loader2 } from "lucide-react"

export function LoadingSpinner() {
  return (
    <div className="h-screen flex items-center justify-center">
      <Loader2 className="w-6 h-6 animate-spin text-amber-600" />
    </div>
  )
}
