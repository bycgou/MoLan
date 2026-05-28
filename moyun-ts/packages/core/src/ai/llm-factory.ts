import { ChatOpenAI } from '@langchain/openai';
import { eq } from 'drizzle-orm';
import type { LlmConfig } from '../types/llm-config.js';
import type { Db } from '../db/index.js';
import { llmConfigs } from '../db/schema.js';

export function createLLM(config: LlmConfig, maxTokens?: number): ChatOpenAI {
  return new ChatOpenAI({
    modelName: config.model,
    openAIApiKey: config.apiKey ?? undefined,
    configuration: {
      baseURL: config.baseUrl ?? undefined,
    },
    temperature: config.temperature,
    maxTokens: maxTokens ?? 4096,
  });
}

export function createLLMWithProvider(config: LlmConfig, maxTokens?: number): ChatOpenAI {
  const provider = config.provider;
  const tokens = maxTokens ?? 4096;

  if (provider === 'ollama') {
    return new ChatOpenAI({
      modelName: config.model,
      openAIApiKey: 'ollama',
      configuration: {
        baseURL: config.baseUrl ?? 'http://localhost:11434/v1',
      },
      temperature: config.temperature,
      maxTokens: tokens,
    });
  }

  if (provider === 'deepseek') {
    return new ChatOpenAI({
      modelName: config.model,
      openAIApiKey: config.apiKey ?? undefined,
      configuration: {
        baseURL: config.baseUrl ?? 'https://api.deepseek.com/v1',
      },
      temperature: config.temperature,
      maxTokens: tokens,
    });
  }

  if (provider === 'zhipu') {
    return new ChatOpenAI({
      modelName: config.model,
      openAIApiKey: config.apiKey ?? undefined,
      configuration: {
        baseURL: config.baseUrl ?? 'https://open.bigmodel.cn/api/paas/v4',
      },
      temperature: config.temperature,
      maxTokens: tokens,
    });
  }

  if (provider === 'qwen') {
    return new ChatOpenAI({
      modelName: config.model,
      openAIApiKey: config.apiKey ?? undefined,
      configuration: {
        baseURL: config.baseUrl ?? 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      },
      temperature: config.temperature,
      maxTokens: tokens,
    });
  }

  // Default: generic OpenAI-compatible
  return createLLM(config, maxTokens);
}

export function getLLMByUsage(db: Db, usageType: string, maxTokens?: number): ChatOpenAI {
  const rows = db.select().from(llmConfigs)
    .where(eq(llmConfigs.usageType, usageType))
    .all();
  const active = rows.find((r: any) => r.isActive) ?? rows[0];
  if (!active) {
    // fallback to general
    const generalRows = db.select().from(llmConfigs)
      .where(eq(llmConfigs.usageType, 'general'))
      .all();
    const general = generalRows.find((r: any) => r.isActive) ?? generalRows[0];
    if (!general) throw new Error('未配置LLM，请先运行 moyun config create');
    return createLLMWithProvider({
      id: general.id,
      provider: general.provider as any,
      model: general.model,
      apiKey: general.apiKey,
      baseUrl: general.baseUrl,
      authType: general.authType ?? 'api_key',
      usageType: general.usageType as any,
      temperature: general.temperature ?? 0.7,
      isActive: general.isActive ?? true,
    }, maxTokens);
  }
  return createLLMWithProvider({
    id: active.id,
    provider: active.provider as any,
    model: active.model,
    apiKey: active.apiKey,
    baseUrl: active.baseUrl,
    authType: active.authType ?? 'api_key',
    usageType: active.usageType as any,
    temperature: active.temperature ?? 0.7,
    isActive: active.isActive ?? true,
  }, maxTokens);
}
