const BASE = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error ?? res.statusText);
  }
  return res.json();
}

export const api = {
  // Novels
  listNovels: () => request<any[]>('/novels'),
  getNovel: (id: string) => request<any>(`/novels/${id}`),
  createNovel: (data: any) => request<any>('/novels', { method: 'POST', body: JSON.stringify(data) }),
  updateNovel: (id: string, data: any) => request<any>(`/novels/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  deleteNovel: (id: string) => request<any>(`/novels/${id}`, { method: 'DELETE' }),
  getWordCount: (id: string) => request<{ wordCount: number }>(`/novels/${id}/word-count`),

  // Chapters
  listChapters: (novelId: string) => request<any[]>(`/novels/${novelId}/chapters`),
  getChapter: (novelId: string, num: number) => request<any>(`/novels/${novelId}/chapters/${num}`),
  createChapter: (novelId: string, data: any) => request<any>(`/novels/${novelId}/chapters`, { method: 'POST', body: JSON.stringify(data) }),
  updateChapter: (novelId: string, num: number, data: any) => request<any>(`/novels/${novelId}/chapters/${num}`, { method: 'PATCH', body: JSON.stringify(data) }),
  deleteChapter: (novelId: string, num: number) => request<any>(`/novels/${novelId}/chapters/${num}`, { method: 'DELETE' }),

  // Characters
  listCharacters: (novelId: string) => request<any[]>(`/novels/${novelId}/characters`),
  getCharacter: (id: string) => request<any>(`/characters/${id}`),
  createCharacter: (novelId: string, data: any) => request<any>(`/novels/${novelId}/characters`, { method: 'POST', body: JSON.stringify(data) }),
  deleteCharacter: (id: string) => request<any>(`/characters/${id}`, { method: 'DELETE' }),

  // Clues
  listClues: (novelId: string, active?: boolean) => request<any[]>(`/novels/${novelId}/clues${active ? '?active=true' : ''}`),
  createClue: (novelId: string, data: any) => request<any>(`/novels/${novelId}/clues`, { method: 'POST', body: JSON.stringify(data) }),
  recycleClue: (id: string, chapter: number) => request<any>(`/clues/${id}/recycle`, { method: 'POST', body: JSON.stringify({ recycleChapter: chapter }) }),

  // Rhythm
  getRhythm: (novelId: string) => request<any>(`/novels/${novelId}/rhythm`),

  // Hook
  analyzeHook: (content: string) => request<any>('/hook/analyze', { method: 'POST', body: JSON.stringify({ content }) }),

  // LLM Config
  listLlmConfigs: () => request<any[]>('/llm-configs'),
  createLlmConfig: (data: any) => request<any>('/llm-configs', { method: 'POST', body: JSON.stringify(data) }),
  deleteLlmConfig: (id: string) => request<any>(`/llm-configs/${id}`, { method: 'DELETE' }),

  // Style Cards
  listStyleCards: () => request<any[]>('/style-cards'),
  createStyleCard: (data: any) => request<any>('/style-cards', { method: 'POST', body: JSON.stringify(data) }),
  deleteStyleCard: (id: string) => request<any>(`/style-cards/${id}`, { method: 'DELETE' }),
  bindStyleCard: (novelId: string, cardId: string) => request<any>(`/novels/${novelId}/style-cards/${cardId}`, { method: 'POST' }),
  getBoundCards: (novelId: string) => request<any[]>(`/novels/${novelId}/style-cards`),
};
