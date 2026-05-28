import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { NovelVO, ChapterListItem, CharacterVO, ClueVO } from '@/types'
import { novelApi, chapterApi, characterApi, clueApi } from '@/api'

export const useNovelStore = defineStore('novel', () => {
  // ---- 状态 ----
  const novels = ref<NovelVO[]>([])
  const currentNovel = ref<NovelVO | null>(null)
  const chapters = ref<ChapterListItem[]>([])
  const characters = ref<CharacterVO[]>([])
  const clues = ref<ClueVO[]>([])
  const loadingNovels = ref(false)
  const loadingNovel = ref(false)
  const deletedNovels = ref<NovelVO[]>([])
  const loadingDeletedNovels = ref(false)

  // ---- 计算属性 ----
  const novelCount = computed(() => novels.value.length)
  const currentNovelId = computed(() => currentNovel.value?.novelId || '')

  // ---- 小说 CRUD ----
  async function fetchNovels() {
    loadingNovels.value = true
    try {
      const res = await novelApi.getNovelList()
      if (res.code === 200) {
        novels.value = res.data
      }
    } finally {
      loadingNovels.value = false
    }
  }

  async function fetchDeletedNovels() {
    loadingDeletedNovels.value = true
    try {
      const res = await novelApi.getDeletedNovelList()
      deletedNovels.value = res.data || []
    } catch (e) {
      console.error('加载回收站失败:', e)
    } finally {
      loadingDeletedNovels.value = false
    }
  }

  async function restoreNovel(novelId: string) {
    await novelApi.restoreNovel(novelId)
    await fetchDeletedNovels()
  }

  async function hardDeleteNovel(novelId: string) {
    await novelApi.hardDeleteNovel(novelId)
    await fetchDeletedNovels()
  }

  async function fetchNovel(novelId: string) {
    loadingNovel.value = true
    try {
      const res = await novelApi.getNovel(novelId)
      if (res.code === 200) {
        currentNovel.value = res.data
        return res.data as NovelVO
      }
      return null
    } finally {
      loadingNovel.value = false
    }
  }

  async function deleteNovel(novelId: string) {
    const res = await novelApi.deleteNovel(novelId)
    if (res.code === 200) {
      novels.value = novels.value.filter((n) => n.novelId !== novelId)
      if (currentNovel.value?.novelId === novelId) {
        currentNovel.value = null
      }
      return true
    }
    return false
  }

  // ---- 章节 ----
  async function fetchChapters(novelId: string) {
    const res = await chapterApi.getChapterList(novelId)
    if (res.code === 200) {
      chapters.value = res.data
    }
  }

  // ---- 人物 ----
  async function fetchCharacters(novelId: string) {
    const res = await characterApi.getCharacterList(novelId)
    if (res.code === 200) {
      characters.value = res.data
    }
  }

  async function createCharacter(novelId: string, data: import('@/types').CreateCharacterRequest) {
    const res = await characterApi.createCharacter(novelId, data)
    if (res.code === 200) {
      await fetchCharacters(novelId)
      return true
    }
    return false
  }

  async function updateCharacter(novelId: string, characterId: string, data: import('@/types').CharacterUpdatePayload) {
    const res = await characterApi.updateCharacter(novelId, characterId, data)
    if (res.code === 200) {
      await fetchCharacters(novelId)
      return true
    }
    return false
  }

  async function addGrowthNode(novelId: string, characterId: string, data: import('@/types').AddGrowthNodeRequest) {
    const res = await characterApi.addGrowthNode(novelId, characterId, data)
    if (res.code === 200) {
      await fetchCharacters(novelId)
      return true
    }
    return false
  }

  async function updateRelationship(novelId: string, characterId: string, targetCharacterId: string, value: number) {
    await characterApi.updateRelationship(novelId, characterId, targetCharacterId, value)
  }

  async function fetchRelationshipGraph(novelId: string) {
    const res = await characterApi.getRelationshipGraph(novelId)
    return res.data
  }

  // ---- 伏笔 ----
  async function fetchClues(novelId: string) {
    const res = await clueApi.getClueList(novelId)
    if (res.code === 200) {
      clues.value = res.data
    }
  }

  async function createClue(novelId: string, data: import('@/types').CreateClueRequest) {
    const res = await clueApi.createClue(novelId, data)
    if (res.code === 200) {
      await fetchClues(novelId)
      return true
    }
    return false
  }

  async function recycleClue(novelId: string, clueId: string, recycleChapter: number) {
    const res = await clueApi.recycleClue(novelId, clueId, recycleChapter)
    if (res.code === 200) {
      await fetchClues(novelId)
      return true
    }
    return false
  }

  async function batchRecycleClues(novelId: string, clueIds: string[], recycleChapter: number) {
    await clueApi.batchRecycleClues(novelId, clueIds, recycleChapter)
    await fetchClues(novelId)
  }

  async function fetchCluesByBuryChapter(novelId: string, chapterNum: number) {
    const res = await clueApi.getCluesByBuryChapter(novelId, chapterNum)
    return res.data || []
  }

  async function fetchCluesByRecycleChapter(novelId: string, chapterNum: number) {
    const res = await clueApi.getCluesByRecycleChapter(novelId, chapterNum)
    return res.data || []
  }

  // ---- 重置 ----
  function $reset() {
    novels.value = []
    currentNovel.value = null
    chapters.value = []
    characters.value = []
    clues.value = []
    deletedNovels.value = []
  }

  return {
    // state
    novels,
    currentNovel,
    chapters,
    characters,
    clues,
    deletedNovels,
    loadingNovels,
    loadingNovel,
    loadingDeletedNovels,
    // computed
    novelCount,
    currentNovelId,
    // 小说
    fetchNovels,
    fetchNovel,
    deleteNovel,
    fetchDeletedNovels,
    restoreNovel,
    hardDeleteNovel,
    // 章节
    fetchChapters,
    // 人物
    fetchCharacters,
    createCharacter,
    updateCharacter,
    addGrowthNode,
    updateRelationship,
    fetchRelationshipGraph,
    // 伏笔
    fetchClues,
    createClue,
    recycleClue,
    batchRecycleClues,
    fetchCluesByBuryChapter,
    fetchCluesByRecycleChapter,
    // 重置
    $reset,
  }
})
