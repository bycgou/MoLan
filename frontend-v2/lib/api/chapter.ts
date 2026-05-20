import { api } from "@/lib/api"
import type {
  ChapterVO,
  ChapterListItem,
  GenerateChapterRequest,
  ChapterVersionVO,
} from "@/types"

export const chapterApi = {
  list: (novelId: string) =>
    api.get<ChapterListItem[]>(`/novel/${novelId}/chapter/list`),

  get: (novelId: string, chapterNum: number) =>
    api.get<ChapterVO>(`/novel/${novelId}/chapter/${chapterNum}`),

  recent: (novelId: string, count: number = 10) =>
    api.get<ChapterVO[]>(`/novel/${novelId}/chapter/recent`, { count }),

  update: (novelId: string, chapterNum: number, data: Partial<ChapterVO>) =>
    api.put<ChapterVO>(`/novel/${novelId}/chapter/${chapterNum}`, data),

  delete: (novelId: string, chapterNum: number) =>
    api.delete<null>(`/novel/${novelId}/chapter/${chapterNum}`),

  generate: (novelId: string, data: GenerateChapterRequest) =>
    api.post<ChapterVO>(`/novel/${novelId}/chapter`, data),

  generateAsync: (novelId: string, data: GenerateChapterRequest) =>
    api.post<string>(`/novel/${novelId}/chapter/async`, data),

  getStatus: (novelId: string, taskId: string) =>
    api.get<unknown>(`/novel/${novelId}/chapter/status/${taskId}`),

  saveManual: (
    novelId: string,
    data: { chapterNum: number; chapterTitle: string; content: string; summaryData: unknown }
  ) =>
    api.post<ChapterVO>(`/novel/${novelId}/chapter/manual`, data),

  extractSummary: (novelId: string, data: { chapterNum: number; content: string }) =>
    api.post<unknown>(`/novel/${novelId}/chapter/extract-summary`, data),

  getVersions: (novelId: string, chapterNum: number) =>
    api.get<ChapterVersionVO[]>(`/novel/${novelId}/chapter/${chapterNum}/versions`),

  rollback: (novelId: string, chapterNum: number, versionId: string) =>
    api.post<null>(`/novel/${novelId}/chapter/${chapterNum}/rollback/${versionId}`),

  getRecycleBin: (novelId: string) =>
    api.get<ChapterVO[]>(`/novel/${novelId}/chapter/recycle-bin`),

  restore: (novelId: string, chapterId: string) =>
    api.post<null>(`/novel/${novelId}/chapter/${chapterId}/restore`),

  hardDelete: (novelId: string, chapterId: string) =>
    api.delete<null>(`/novel/${novelId}/chapter/${chapterId}/hard`),

  streamWriteUrl: () => "/api/chapter/stream-write",
}
