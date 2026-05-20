import { api } from "@/lib/api"
import type {
  CharacterVO,
  CreateCharacterRequest,
  UpdateCharacterRequest,
  AddGrowthNodeRequest,
  GrowthNode,
} from "@/types"

export const characterApi = {
  create: (novelId: string, data: CreateCharacterRequest) =>
    api.post<CharacterVO>(`/novel/${novelId}/character`, data),

  list: (novelId: string) =>
    api.get<CharacterVO[]>(`/novel/${novelId}/character/list`),

  get: (novelId: string, characterId: string) =>
    api.get<CharacterVO>(`/novel/${novelId}/character/${characterId}`),

  update: (novelId: string, characterId: string, data: UpdateCharacterRequest) =>
    api.put<CharacterVO>(`/novel/${novelId}/character/${characterId}`, data),

  addGrowthNode: (novelId: string, characterId: string, data: AddGrowthNodeRequest) =>
    api.post<GrowthNode>(`/novel/${novelId}/character/${characterId}/growth`, data),
}
