import { api } from "../api"
import type { GenreTemplateVO } from "@/types"

export const genreTemplateApi = {
  list: () =>
    api.get<GenreTemplateVO[]>("/genre/list"),

  getByGenre: (genre: string) =>
    api.get<GenreTemplateVO>("/genre/" + genre),

  get: (code: string) =>
    api.get<GenreTemplateVO>(`/genre/${code}`),
}
