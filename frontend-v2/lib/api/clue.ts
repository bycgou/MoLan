import { api } from "@/lib/api"
import type { ClueVO, CreateClueRequest } from "@/types"

export const clueApi = {
  create: (novelId: string, data: CreateClueRequest) =>
    api.post<ClueVO>(`/novel/${novelId}/clue`, data),

  list: (novelId: string) =>
    api.get<ClueVO[]>(`/novel/${novelId}/clue/list`),

  getUnrecycled: (novelId: string) =>
    api.get<ClueVO[]>(`/novel/${novelId}/clue/unrecycled`),

  recycle: (novelId: string, clueId: string, recycleChapter: number) =>
    api.put<null>(`/novel/${novelId}/clue/${clueId}/recycle`, { recycleChapter }),
}
