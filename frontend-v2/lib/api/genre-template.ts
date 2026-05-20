import { api } from "@/lib/api"
import type { GenreTemplateVO } from "@/types"

export const genreTemplateApi = {
  list: () =>
    api.get<GenreTemplateVO[]>("/genre-template/list"),

  getByGenre: (genre: string) =>
    api.get<GenreTemplateVO[]>("/genre-template/by-genre", { genre }),

  get: (templateId: string) =>
    api.get<GenreTemplateVO>(`/genre-template/${templateId}`),
}
