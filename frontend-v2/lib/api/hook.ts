import { api } from "@/lib/api"
import type { HookAnalysisVO } from "@/types"

export const hookApi = {
  analyzeHook: (novelId: string, chapterNum: number) =>
    api.get<HookAnalysisVO>(`/novel/${novelId}/chapter/${chapterNum}/hook`),
}
