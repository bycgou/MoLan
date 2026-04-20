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

  async function updateCharacter(novelId: string, characterId: string, data: import('@/types').UpdateCharacterRequest) {
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

  // ---- 重置 ----
  function $reset() {
    novels.value = []
    currentNovel.value = null
    chapters.value = []
    characters.value = []
    clues.value = []
  }

  return {
    novels,
    currentNovel,
    chapters,
    characters,
    clues,
    loadingNovels,
    loadingNovel,
    novelCount,
    currentNovelId,
    fetchNovels,
    fetchNovel,
    deleteNovel,
    fetchChapters,
    fetchCharacters,
    createCharacter,
    updateCharacter,
    addGrowthNode,
    fetchClues,
    createClue,
    recycleClue,
    $reset,
  }
})
