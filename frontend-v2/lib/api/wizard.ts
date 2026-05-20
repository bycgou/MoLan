import { api } from "@/lib/api"
import type {
  WizardSettingsRequest,
  WizardSaveRequest,
  ArcOutlineDTO,
  ArcChapterOutlineDTO,
  NovelVO,
  BatchGenerationProgress,
} from "@/types"

function getToken(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem("token")
}

export const wizardApi = {
  generateVolumeOutlines: (data: WizardSettingsRequest) =>
    api.post<ArcOutlineDTO[]>("/wizard/generate-volume-outlines", data),

  saveNovel: (data: WizardSaveRequest) =>
    api.post<NovelVO>("/wizard/save", data),

  generateArcChapterOutlines: (novelId: string, arcIndex: number) =>
    api.post<ArcChapterOutlineDTO[]>(
      `/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines`
    ),

  generateArcChapterOutlinesStream: (novelId: string, arcIndex: number) => {
    const token = getToken()
    return fetch(`/api/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines/stream`, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
  },

  confirmChapterOutlines: (
    novelId: string,
    arcIndex: number,
    data: ArcChapterOutlineDTO[]
  ) =>
    api.put<null>(
      `/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines`,
      data
    ),

  batchGenerate: (novelId: string) =>
    api.post<string>(`/wizard/novel/${novelId}/batch-generate`),

  getBatchStatus: (novelId: string, taskId: string) =>
    api.get<BatchGenerationProgress>(
      `/wizard/novel/${novelId}/batch-status/${taskId}`
    ),

  batchCancel: (novelId: string, taskId: string) =>
    api.post<null>(`/wizard/novel/${novelId}/batch-cancel/${taskId}`),
}
