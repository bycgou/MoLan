import { api } from "@/lib/api"
import type {
  CreateNovelRequest,
  NovelVO,
  UpdateNovelRequest,
  NovelGraphVO,
  TimelineVO,
} from "@/types"

export const novelApi = {
  create: (data: CreateNovelRequest) =>
    api.post<NovelVO>("/novel", data),

  list: () =>
    api.get<NovelVO[]>("/novel/list"),

  get: (novelId: string) =>
    api.get<NovelVO>(`/novel/${novelId}`),

  update: (novelId: string, data: UpdateNovelRequest) =>
    api.put<NovelVO>(`/novel/${novelId}`, data),

  delete: (novelId: string) =>
    api.delete<null>(`/novel/${novelId}`),

  export: (novelId: string) =>
    api.get(`/novel/${novelId}/export`) as unknown as Promise<Blob>,

  autoGenerateOutline: (data: { novelName: string; genre: string; prompt?: string }) =>
    api.post<unknown>("/novel/auto-generate-outline", data),

  generateOutline: (
    novelId: string,
    params?: { chapterNum?: number; userIntent?: string; llmConfigId?: string; modelType?: string }
  ) =>
    api.get<{ outline: string; implementationIdeas: string[] }>(
      `/novel/${novelId}/outline`,
      params as Record<string, string | number | boolean> | undefined
    ),

  getGraph: (novelId: string) =>
    api.get<NovelGraphVO>(`/novel/${novelId}/novel-graph`),

  getTimeline: (novelId: string) =>
    api.get<TimelineVO>(`/novel/${novelId}/timeline`),

  updateTimeline: (novelId: string, data: TimelineVO) =>
    api.put<TimelineVO>(`/novel/${novelId}/timeline`, data),
}
