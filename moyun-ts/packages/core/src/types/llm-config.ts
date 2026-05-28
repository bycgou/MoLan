export type LlmProvider = 'openai' | 'deepseek' | 'ollama' | 'zhipu' | 'qwen' | 'moonshot' | 'wenxin';
export type UsageType = 'general' | 'outline' | 'writing' | 'summary' | 'audit' | 'embedding';

export interface LlmConfig {
  id: string;
  provider: LlmProvider;
  model: string;
  apiKey: string | null;
  baseUrl: string | null;
  authType: string;
  usageType: UsageType;
  temperature: number;
  isActive: boolean;
}

export interface CreateLlmConfigInput {
  provider: LlmProvider;
  model: string;
  apiKey?: string;
  baseUrl?: string;
  usageType?: UsageType;
  temperature?: number;
}
