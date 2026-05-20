import { api } from "@/lib/api"
import type {
  LlmConfigVO,
  CreateLlmConfigRequest,
  UpdateLlmConfigRequest,
  TestConnectionRequest,
} from "@/types"

export const llmConfigApi = {
  list: () =>
    api.get<LlmConfigVO[]>("/llm-config/list"),

  get: (configId: string) =>
    api.get<LlmConfigVO>(`/llm-config/${configId}`),

  create: (data: CreateLlmConfigRequest) =>
    api.post<LlmConfigVO>("/llm-config", data),

  update: (configId: string, data: UpdateLlmConfigRequest) =>
    api.put<LlmConfigVO>(`/llm-config/${configId}`, data),

  delete: (configId: string) =>
    api.delete<null>(`/llm-config/${configId}`),

  testConnection: (data: TestConnectionRequest) =>
    api.post<boolean>("/llm-config/test", data),

  testSavedConfig: (configId: string) =>
    api.post<boolean>(`/llm-config/test/${configId}`),

  getActiveConfig: (usageType: string = "all") =>
    api.get<LlmConfigVO>("/llm-config/active", { usageType }),
}
