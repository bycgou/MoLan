import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

// 错误消息映射
const ERROR_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查输入',
  401: '登录已过期，请重新登录',
  403: '没有权限执行此操作',
  404: '请求的资源不存在',
  408: '请求超时，请稍后重试',
  429: '请求过于频繁，请稍后重试',
  500: '服务器内部错误，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务暂时不可用，请稍后重试',
}

// 重试延迟工具函数
function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function isRetryableError(status?: number): boolean {
  return status === 502 || status === 503 || status === 504 || status === undefined
}

const api = axios.create({
  baseURL: '/api',
  timeout: 180000, // 增加到180秒（3分钟），LLM调用可能需要更长时间
  headers: {
    'Content-Type': 'application/json',
  },
})

// ========== 请求拦截器：自动附加 Token ==========
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ========== 响应拦截器：统一错误处理 + 重试 ==========
api.interceptors.response.use(
  (response) => {
    // ApiResponse.code 业务码校验
    const data = response.data
    if (data && typeof data.code === 'number' && data.code !== 200 && data.code !== 0) {
      const msg = data.message || '操作失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return response
  },
  async (error) => {
    const config = error.config
    const status = error.response?.status

    // GET 请求自动重试（5xx / 网络无响应）
    if (
      config?.method?.toUpperCase() === 'GET' &&
      isRetryableError(status) &&
      (config.__retryCount || 0) < 2
    ) {
      config.__retryCount = (config.__retryCount || 0) + 1
      await delay(1000 * config.__retryCount)
      return api(config)
    }

    // 401 — just reject, let route guard handle redirect
    if (status === 401) {
      localStorage.removeItem('token')
      return Promise.reject(error)
    }

    // 其他错误友好化提示
    const msg = ERROR_MESSAGES[status || 0] || error.response?.data?.message || `请求失败 (${status})`
    ElMessage.error(msg)

    return Promise.reject(error)
  },
)

export default api

// ============================================================
// 认证 API
// ============================================================
export const authApi = {
  login: (data: import('@/types').LoginRequest) =>
    api.post<ApiResponse<import('@/types').UserVO>>('/auth/login', data).then(res => res.data),
  register: (data: import('@/types').RegisterRequest) =>
    api.post<ApiResponse<import('@/types').UserVO>>('/auth/register', data).then(res => res.data),
  getCurrentUser: () =>
    api.get<ApiResponse<import('@/types').UserVO>>('/auth/me').then(res => res.data),
}

// ============================================================
// 小说 API
// ============================================================
export const novelApi = {
  createNovel: (data: import('@/types').CreateNovelRequest) =>
    api.post<ApiResponse<import('@/types').NovelVO>>('/novel', data).then(res => res.data),
  getNovelList: () =>
    api.get<ApiResponse<import('@/types').NovelVO[]>>('/novel/list').then(res => res.data),
  getNovel: (novelId: string) =>
    api.get<ApiResponse<import('@/types').NovelVO>>(`/novel/${novelId}`).then(res => res.data),
  updateNovel: (novelId: string, data: import('@/types').UpdateNovelRequest) =>
    api.put<ApiResponse<import('@/types').NovelVO>>(`/novel/${novelId}`, data).then(res => res.data),
  deleteNovel: (novelId: string) =>
    api.delete<ApiResponse<null>>(`/novel/${novelId}`).then(res => res.data),
  exportNovel: (novelId: string) =>
    api.get(`/novel/${novelId}/export`, { responseType: 'blob' }).then(res => res.data),
  autoGenerateOutline: (data: { novelName: string; genre: string; prompt?: string }) =>
    api.post<ApiResponse<unknown>>('/novel/auto-generate-outline', data).then(res => res.data),
  generateOutline: (novelId: string, params?: { chapterNum?: number; userIntent?: string; llmConfigId?: string; modelType?: string }) =>
    api.post<ApiResponse<{ outline: string; implementationIdeas: string[] }>>(`/novel/${novelId}/outline`, null, { params }).then(res => res.data),
  getNovelGraph: (novelId: string) =>
    api.get<ApiResponse<import('@/types').NovelGraphVO>>(`/novel/${novelId}/novel-graph`).then(res => res.data),
  getTimeline: (novelId: string) =>
    api.get<ApiResponse<import('@/types').TimelineVO>>(`/novel/${novelId}/timeline`).then(res => res.data),
  updateTimeline: (novelId: string, data: import('@/types').TimelineVO) =>
    api.put<ApiResponse<import('@/types').TimelineVO>>(`/novel/${novelId}/timeline`, data).then(res => res.data),
  getDeletedNovelList: () =>
    api.get<ApiResponse<import('@/types').NovelVO[]>>('/novel/list', { params: { isDeleted: true } }).then(res => res.data),
  restoreNovel: (novelId: string) =>
    api.post<ApiResponse<null>>(`/novel/${novelId}/restore`).then(res => res.data),
  hardDeleteNovel: (novelId: string) =>
    api.delete<ApiResponse<null>>(`/novel/${novelId}/hard`).then(res => res.data),
}

// ============================================================
// 章节 API
// ============================================================
export const chapterApi = {
  generateChapter: (novelId: string, data: import('@/types').GenerateChapterRequest) =>
    api.post<ApiResponse<import('@/types').ChapterVO>>(`/novel/${novelId}/chapter`, data).then(res => res.data),
  generateChapterAsync: (novelId: string, data: import('@/types').GenerateChapterRequest) =>
    api.post<ApiResponse<string>>(`/novel/${novelId}/chapter/async`, data).then(res => res.data),
  getGenerationStatus: (novelId: string, taskId: string) =>
    api.get<ApiResponse<unknown>>(`/novel/${novelId}/chapter/status/${taskId}`).then(res => res.data),
  getChapterList: (novelId: string) =>
    api.get<ApiResponse<import('@/types').ChapterListItem[]>>(`/novel/${novelId}/chapter/list`).then(res => res.data),
  getChapter: (novelId: string, chapterNum: number) =>
    api.get<ApiResponse<import('@/types').ChapterVO>>(`/novel/${novelId}/chapter/${chapterNum}`).then(res => res.data),
  getRecentChapters: (novelId: string, count: number = 10) =>
    api.get<ApiResponse<import('@/types').ChapterVO[]>>(`/novel/${novelId}/chapter/recent`, { params: { count } }).then(res => res.data),
  updateChapter: (novelId: string, chapterNum: number, data: Partial<import('@/types').ChapterVO>) =>
    api.put<ApiResponse<import('@/types').ChapterVO>>(`/novel/${novelId}/chapter/${chapterNum}`, data).then(res => res.data),
  extractSummary: (novelId: string, data: { chapterNum: number; content: string }) =>
    api.post<ApiResponse<unknown>>(`/novel/${novelId}/chapter/extract-summary`, data).then(res => res.data),
  saveManualChapter: (novelId: string, data: { chapterNum: number; chapterTitle: string; content: string; summaryData: unknown }) =>
    api.post<ApiResponse<import('@/types').ChapterVO>>(`/novel/${novelId}/chapter/manual`, data).then(res => res.data),
  deleteChapter: (novelId: string, chapterNum: number) =>
    api.delete<ApiResponse<null>>(`/novel/${novelId}/chapter/${chapterNum}`).then(res => res.data),
  getRecycleBin: (novelId: string) =>
    api.get<ApiResponse<import('@/types').ChapterVO[]>>(`/novel/${novelId}/chapter/recycle-bin`).then(res => res.data),
  restoreChapter: (novelId: string, chapterId: string) =>
    api.post<ApiResponse<null>>(`/novel/${novelId}/chapter/${chapterId}/restore`).then(res => res.data),
  hardDeleteChapter: (novelId: string, chapterId: string) =>
    api.delete<ApiResponse<null>>(`/novel/${novelId}/chapter/${chapterId}/hard`).then(res => res.data),
  getChapterVersions: (novelId: string, chapterNum: number) =>
    api.get<ApiResponse<import('@/types').ChapterVersionVO[]>>(`/novel/${novelId}/chapter/${chapterNum}/versions`).then(res => res.data),
  rollbackChapter: (novelId: string, chapterNum: number, versionId: string) =>
    api.post<ApiResponse<null>>(`/novel/${novelId}/chapter/${chapterNum}/rollback/${versionId}`).then(res => res.data),
  /**
   * SSE 流式生成章节
   * 返回 fetch 请求体，前端需自行发送 POST 请求并监听 ReadableStream
   */
  getStreamWriteUrl: () => {
    return '/chapter/stream-write'
  },
}

// ============================================================
// 人物 API
// ============================================================
export const characterApi = {
  createCharacter: (novelId: string, data: import('@/types').CreateCharacterRequest) =>
    api.post<ApiResponse<import('@/types').CharacterVO>>(`/novel/${novelId}/character`, data).then(res => res.data),
  getCharacterList: (novelId: string) =>
    api.get<ApiResponse<import('@/types').CharacterVO[]>>(`/novel/${novelId}/character/list`).then(res => res.data),
  getCharacter: (novelId: string, characterId: string) =>
    api.get<ApiResponse<import('@/types').CharacterVO>>(`/novel/${novelId}/character/${characterId}`).then(res => res.data),
  updateCharacter: (novelId: string, characterId: string, data: import('@/types').CharacterUpdatePayload) =>
    api.put<ApiResponse<import('@/types').CharacterVO>>(`/novel/${novelId}/character/${characterId}`, data).then(res => res.data),
  addGrowthNode: (novelId: string, characterId: string, data: import('@/types').AddGrowthNodeRequest) =>
    api.post<ApiResponse<null>>(`/novel/${novelId}/character/${characterId}/growth`, data).then(res => res.data),
  updateRelationship: (novelId: string, characterId: string, targetCharacterId: string, value: number) =>
    api.put<ApiResponse<null>>(`/novel/${novelId}/character/${characterId}/relationship`, null, { params: { targetCharacterId, value } }).then(res => res.data),
  getRelationshipGraph: (novelId: string) =>
    api.get<ApiResponse<import('@/types').CharacterGraph>>(`/novel/${novelId}/graph`).then(res => res.data),
}

// ============================================================
// 伏笔/线索 API
// ============================================================
export const clueApi = {
  createClue: (novelId: string, data: import('@/types').CreateClueRequest) =>
    api.post<ApiResponse<import('@/types').ClueVO>>(`/novel/${novelId}/clue`, data).then(res => res.data),
  getClueList: (novelId: string) =>
    api.get<ApiResponse<import('@/types').ClueVO[]>>(`/novel/${novelId}/clue/list`).then(res => res.data),
  getUnrecycledClues: (novelId: string) =>
    api.get<ApiResponse<import('@/types').ClueVO[]>>(`/novel/${novelId}/clue/unrecycled`).then(res => res.data),
  recycleClue: (novelId: string, clueId: string, recycleChapter: number) =>
    api.put<ApiResponse<null>>(`/novel/${novelId}/clue/${clueId}/recycle`, null, { params: { recycleChapter } }).then(res => res.data),
  batchRecycleClues: (novelId: string, clueIds: string[], recycleChapter: number) =>
    api.put<ApiResponse<null>>(`/novel/${novelId}/clue/batch-recycle`, clueIds, { params: { recycleChapter } }).then(res => res.data),
  getCluesByBuryChapter: (novelId: string, chapterNum: number) =>
    api.get<ApiResponse<import('@/types').ClueVO[]>>(`/novel/${novelId}/clue/bury/${chapterNum}`).then(res => res.data),
  getCluesByRecycleChapter: (novelId: string, chapterNum: number) =>
    api.get<ApiResponse<import('@/types').ClueVO[]>>(`/novel/${novelId}/clue/recycle/${chapterNum}`).then(res => res.data),
}

// ============================================================
// 模型配置 API
// ============================================================
export const llmConfigApi = {
  getConfigList: () =>
    api.get<ApiResponse<import('@/types').LlmConfigVO[]>>('/llm-config/list').then(res => res.data),
  getConfig: (configId: string) =>
    api.get<ApiResponse<import('@/types').LlmConfigVO>>(`/llm-config/${configId}`).then(res => res.data),
  createConfig: (data: import('@/types').CreateLlmConfigRequest) =>
    api.post<ApiResponse<import('@/types').LlmConfigVO>>('/llm-config', data).then(res => res.data),
  updateConfig: (configId: string, data: import('@/types').UpdateLlmConfigRequest) =>
    api.put<ApiResponse<import('@/types').LlmConfigVO>>(`/llm-config/${configId}`, data).then(res => res.data),
  deleteConfig: (configId: string) =>
    api.delete<ApiResponse<null>>(`/llm-config/${configId}`).then(res => res.data),
  testConnection: (data: import('@/types').TestConnectionRequest) =>
    api.post<ApiResponse<string>>('/llm-config/test', data).then(res => res.data),
  testSavedConfig: (configId: string) =>
    api.post<ApiResponse<string>>(`/llm-config/test/${configId}`).then(res => res.data),
  getActiveConfig: (usageType: string = 'all') =>
    api.get<ApiResponse<import('@/types').LlmConfigVO>>('/llm-config/active', { params: { usageType } }).then(res => res.data),
}

// ============================================================
// 节奏系统 API
// ============================================================
export const rhythmApi = {
  getRhythmStatus: (novelId: string) =>
    api.get<ApiResponse<import('@/types').RhythmStatusVO>>(`/novel/${novelId}/rhythm/status`).then(res => res.data),
  getRecommendedStrand: (novelId: string) =>
    api.get<ApiResponse<string>>(`/novel/${novelId}/rhythm/recommend`).then(res => res.data),
  checkViolation: (novelId: string, strandType: string) =>
    api.get<ApiResponse<import('@/types').RhythmViolation | null>>(`/novel/${novelId}/rhythm/check`, { params: { strandType } }).then(res => res.data),
  getReadPullTrend: (novelId: string) =>
    api.get<ApiResponse<import('@/types').ReadPullTrendVO>>(`/novel/${novelId}/rhythm/trend`).then(res => res.data),
  getSuggestion: (novelId: string) =>
    api.get<ApiResponse<string>>(`/novel/${novelId}/rhythm/suggestion`).then(res => res.data),
  analyzeReadPull: (novelId: string, chapterNum: number, chapterContent: string) =>
    api.post<ApiResponse<unknown>>(`/novel/${novelId}/rhythm/read-pull`, chapterContent, {
      params: { chapterNum },
      headers: { 'Content-Type': 'text/plain' },
    }).then(res => res.data),
}

// ============================================================
// 钩子检测 API
// ============================================================
export const hookApi = {
  analyzeHook: (novelId: string, chapterNum: number) =>
    api.get<ApiResponse<import('@/types').HookAnalysisVO>>(`/novel/${novelId}/chapter/${chapterNum}/hook`).then(res => res.data),
}

// ============================================================
// 题材模板 API
// ============================================================
export const genreApi = {
  getGenreList: () =>
    api.get<ApiResponse<import('@/types').GenreInfo[]>>('/genre/list').then(res => res.data),
  getGenreTemplate: (genreCode: string) =>
    api.get<ApiResponse<import('@/types').GenreTemplate>>(`/genre/${genreCode}`).then(res => res.data),
  getGenreRhythm: (genreCode: string) =>
    api.get<ApiResponse<import('@/types').GenreRhythmConfig>>(`/genre/${genreCode}/rhythm`).then(res => res.data),
  getGuideList: (genreCode: string) =>
    api.get<ApiResponse<string[]>>(`/genre/${genreCode}/guides`).then(res => res.data),
  getGuideFile: (genreCode: string, filename: string) =>
    api.get<ApiResponse<string>>(`/genre/${genreCode}/guides/${filename}`).then(res => res.data),
}

// ============================================================
// 向导流程 API
// ============================================================
export const wizardApi = {
  generateVolumeOutlines: (data: import('@/types').WizardSettingsRequest) =>
    api.post<ApiResponse<import('@/types').ArcOutlineDTO[]>>('/wizard/generate-volume-outlines', data).then(res => res.data),
  saveNovel: (data: import('@/types').WizardSaveRequest) =>
    api.post<ApiResponse<import('@/types').NovelVO>>('/wizard/save', data).then(res => res.data),
  generateArcChapterOutlines: (novelId: string, arcIndex: number) =>
    api.post<ApiResponse<import('@/types').ArcChapterOutlineDTO[]>>(`/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines`).then(res => res.data),
  generateArcChapterOutlinesStream: (novelId: string, arcIndex: number) => {
    return fetch(`/api/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines/stream`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
      },
    });
  },
  confirmChapterOutlines: (novelId: string, arcIndex: number, data: import('@/types').ArcChapterOutlineDTO[]) =>
    api.put<ApiResponse<null>>(`/wizard/novel/${novelId}/arc/${arcIndex}/chapter-outlines`, data).then(res => res.data),
  batchGenerate: (novelId: string) =>
    api.post<ApiResponse<string>>(`/wizard/novel/${novelId}/batch-generate`).then(res => res.data),
  getBatchStatus: (novelId: string, taskId: string) =>
    api.get<ApiResponse<import('@/types').BatchGenerationProgress>>(`/wizard/novel/${novelId}/batch-status/${taskId}`).then(res => res.data),
  batchCancel: (novelId: string, taskId: string) =>
    api.post<ApiResponse<null>>(`/wizard/novel/${novelId}/batch-cancel/${taskId}`).then(res => res.data),
}