import { api } from "@/lib/api"
import type {
  StyleCard,
  StyleCheckResult,
  CreateStyleCardRequest,
} from "@/types"

export const styleApi = {
  createFromText: (data: CreateStyleCardRequest) =>
    api.post<StyleCard>("/style-card/create", data),

  createFromFile: (file: File, novelName: string, configId?: string) => {
    const formData = new FormData()
    formData.append("file", file)
    formData.append("novelName", novelName)
    if (configId) formData.append("configId", configId)
    return api.postForm<StyleCard>("/style-card/upload", formData)
  },

  getUserCards: () =>
    api.get<StyleCard[]>("/style-card/list"),

  getPublicCards: (page: number = 1, size: number = 20) =>
    api.get<StyleCard[]>("/style-card/public", { page, size }),

  getDetail: (cardId: string) =>
    api.get<StyleCard>(`/style-card/${cardId}`),

  bindNovel: (novelId: string, cardId: string) =>
    api.post<null>("/style-card/bind", { novelId, cardId }),

  unbindNovel: (novelId: string) =>
    api.post<null>("/style-card/unbind", { novelId }),

  getBoundCardId: (novelId: string) =>
    api.get<string>(`/style-card/binding/${novelId}`),

  quickCheck: (text: string, cardId: string) =>
    api.post<StyleCheckResult>("/style-card/check/quick", { text, cardId }),

  deepCheck: (text: string, cardId: string) =>
    api.post<StyleCheckResult>("/style-card/check/deep", { text, cardId }),

  delete: (cardId: string) =>
    api.delete<null>(`/style-card/${cardId}`),

  updatePublic: (cardId: string, isPublic: boolean) =>
    api.put<null>(`/style-card/${cardId}/public`, { isPublic }),
}
