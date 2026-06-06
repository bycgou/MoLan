import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock API modules before importing store
vi.mock('@/api', () => ({
  novelApi: {
    getNovelList: vi.fn(),
    getNovel: vi.fn(),
    getDeletedNovelList: vi.fn(),
    restoreNovel: vi.fn(),
    hardDeleteNovel: vi.fn(),
    deleteNovel: vi.fn(),
  },
  chapterApi: {
    getChapterList: vi.fn(),
  },
  characterApi: {
    getCharacterList: vi.fn(),
  },
  clueApi: {
    getClueList: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

import { useNovelStore } from '@/stores/novelStore'
import { chapterApi, characterApi, clueApi, novelApi } from '@/api'
import { ElMessage } from 'element-plus'

const mockChapterApi = vi.mocked(chapterApi)
const mockCharacterApi = vi.mocked(characterApi)
const mockClueApi = vi.mocked(clueApi)
const mockNovelApi = vi.mocked(novelApi)
const mockElMessage = vi.mocked(ElMessage)

describe('novelStore error handling', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('fetchChapters', () => {
    it('should not throw when API call fails', async () => {
      mockChapterApi.getChapterList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await expect(store.fetchChapters('novel-1')).resolves.toBeUndefined()
    })

    it('should show error message on failure', async () => {
      mockChapterApi.getChapterList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.fetchChapters('novel-1')

      expect(mockElMessage.error).toHaveBeenCalledWith('加载章节列表失败')
    })

    it('should keep chapters empty on failure', async () => {
      mockChapterApi.getChapterList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.fetchChapters('novel-1')

      expect(store.chapters).toEqual([])
    })

    it('should populate chapters on success', async () => {
      const fakeChapters = [{ chapterId: '1', chapterNum: 1, chapterTitle: 'Test' } as any]
      mockChapterApi.getChapterList.mockResolvedValue({ code: 200, data: fakeChapters, message: '' } as any)
      const store = useNovelStore()

      await store.fetchChapters('novel-1')

      expect(store.chapters).toEqual(fakeChapters)
      expect(mockElMessage.error).not.toHaveBeenCalled()
    })
  })

  describe('fetchCharacters', () => {
    it('should not throw when API call fails', async () => {
      mockCharacterApi.getCharacterList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await expect(store.fetchCharacters('novel-1')).resolves.toBeUndefined()
    })

    it('should show error message on failure', async () => {
      mockCharacterApi.getCharacterList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.fetchCharacters('novel-1')

      expect(mockElMessage.error).toHaveBeenCalledWith('加载角色列表失败')
    })
  })

  describe('fetchClues', () => {
    it('should not throw when API call fails', async () => {
      mockClueApi.getClueList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await expect(store.fetchClues('novel-1')).resolves.toBeUndefined()
    })

    it('should show error message on failure', async () => {
      mockClueApi.getClueList.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.fetchClues('novel-1')

      expect(mockElMessage.error).toHaveBeenCalledWith('加载伏笔列表失败')
    })
  })

  describe('restoreNovel', () => {
    it('should not throw when API call fails', async () => {
      mockNovelApi.restoreNovel.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await expect(store.restoreNovel('novel-1')).resolves.toBeUndefined()
    })

    it('should show error message on failure', async () => {
      mockNovelApi.restoreNovel.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.restoreNovel('novel-1')

      expect(mockElMessage.error).toHaveBeenCalledWith('恢复小说失败')
    })
  })

  describe('hardDeleteNovel', () => {
    it('should not throw when API call fails', async () => {
      mockNovelApi.hardDeleteNovel.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await expect(store.hardDeleteNovel('novel-1')).resolves.toBeUndefined()
    })

    it('should show error message on failure', async () => {
      mockNovelApi.hardDeleteNovel.mockRejectedValue(new Error('Network Error'))
      const store = useNovelStore()

      await store.hardDeleteNovel('novel-1')

      expect(mockElMessage.error).toHaveBeenCalledWith('彻底删除失败')
    })
  })
})
