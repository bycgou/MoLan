import api from '@/api'
import type { StyleCard, StyleCheckResult, CreateStyleCardRequest } from '@/types/style'

export type { StyleCard, StyleSampleItem, StyleMetrics, StyleCheckResult, CreateStyleCardRequest } from '@/types/style'

export const styleApi = {
  createFromText: (data: CreateStyleCardRequest) =>
    api.post<{ code: number; message: string; data: StyleCard }>('/style-card/create', data).then(res => res.data),

  createFromFile: (file: File, novelName: string, configId?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('novelName', novelName)
    if (configId) formData.append('configId', configId)
    return api.post<{ code: number; message: string; data: StyleCard }>('/style-card/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(res => res.data)
  },

  getUserCards: () =>
    api.get<{ code: number; message: string; data: StyleCard[] }>('/style-card/list').then(res => res.data),

  getPublicCards: (page = 1, size = 20) =>
    api.get<{ code: number; message: string; data: StyleCard[] }>('/style-card/public', { params: { page, size } }).then(res => res.data),

  getDetail: (cardId: string) =>
    api.get<{ code: number; message: string; data: StyleCard }>(`/style-card/${cardId}`).then(res => res.data),

  bindNovel: (novelId: string, cardId: string) =>
    api.post<{ code: number; message: string }>('/style-card/bind', { novelId, cardId }).then(res => res.data),

  unbindNovel: (novelId: string) =>
    api.post<{ code: number; message: string }>('/style-card/unbind', { novelId }).then(res => res.data),

  getBoundCardId: (novelId: string) =>
    api.get<{ code: number; message: string; data: string }>(`/style-card/binding/${novelId}`).then(res => res.data),

  quickCheck: (text: string, cardId: string) =>
    api.post<{ code: number; message: string; data: StyleCheckResult }>('/style-card/check/quick', { text, cardId }).then(res => res.data),

  deepCheck: (text: string, cardId: string) =>
    api.post<{ code: number; message: string; data: StyleCheckResult }>('/style-card/check/deep', { text, cardId }).then(res => res.data),

  delete: (cardId: string) =>
    api.delete<{ code: number; message: string }>(`/style-card/${cardId}`).then(res => res.data),

  updatePublic: (cardId: string, isPublic: boolean) =>
    api.put<{ code: number; message: string }>(`/style-card/${cardId}/public`, { isPublic }).then(res => res.data),

  fixDoubleEncoding: () =>
    api.post<{ code: number; message: string; data: Record<string, unknown> }>('/style-card/admin/fix-double-encoding').then(res => res.data),
}
