import { api } from "@/lib/api"
import type { RhythmStatusVO, ReadPullTrendVO } from "@/types"

export const rhythmApi = {
  getStatus: (novelId: string) =>
    api.get<RhythmStatusVO>(`/novel/${novelId}/rhythm/status`),

  getRecommendedStrand: (novelId: string) =>
    api.get<string>(`/novel/${novelId}/rhythm/recommend`),

  checkViolation: (novelId: string, strandType: string) =>
    api.get<boolean>(`/novel/${novelId}/rhythm/check`, { strandType }),

  getReadPullTrend: (novelId: string) =>
    api.get<ReadPullTrendVO>(`/novel/${novelId}/rhythm/trend`),

  getSuggestion: (novelId: string) =>
    api.get<string>(`/novel/${novelId}/rhythm/suggestion`),

  analyzeReadPull: (novelId: string, data: unknown) =>
    api.post<unknown>(`/novel/${novelId}/rhythm/read-pull`, data),
}
