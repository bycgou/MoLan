import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useState } from 'react';

export function LlmConfigPage() {
  const qc = useQueryClient();
  const { data: configs = [] } = useQuery({ queryKey: ['llm-configs'], queryFn: api.listLlmConfigs });
  const [provider, setProvider] = useState('deepseek');
  const [model, setModel] = useState('');
  const [apiKey, setApiKey] = useState('');
  const create = useMutation({
    mutationFn: () => api.createLlmConfig({ provider, model, apiKey: apiKey || undefined }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['llm-configs'] }); setModel(''); setApiKey(''); },
  });

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">LLM配置</h1>
      <div className="p-4 bg-white rounded shadow mb-6 space-y-2">
        <div className="flex gap-2">
          <select value={provider} onChange={e => setProvider(e.target.value)} className="px-3 py-2 border rounded">
            <option value="deepseek">DeepSeek</option>
            <option value="openai">OpenAI</option>
            <option value="ollama">Ollama</option>
            <option value="zhipu">智谱</option>
            <option value="qwen">通义千问</option>
          </select>
          <input value={model} onChange={e => setModel(e.target.value)} placeholder="模型名"
            className="flex-1 px-3 py-2 border rounded" />
          <input value={apiKey} onChange={e => setApiKey(e.target.value)} placeholder="API Key (可选)" type="password"
            className="flex-1 px-3 py-2 border rounded" />
          <button onClick={() => model && create.mutate()} className="px-4 py-2 bg-blue-600 text-white rounded">添加</button>
        </div>
      </div>
      <div className="space-y-2">
        {configs.map((c: any) => (
          <div key={c.id} className="p-4 bg-white rounded shadow flex justify-between items-center">
            <div>
              <span className="font-medium">{c.provider}</span>
              <span className="ml-2">{c.model}</span>
              <span className="ml-2 text-sm text-gray-500">[{c.usageType}]</span>
            </div>
            <span className={`text-sm ${c.isActive ? 'text-green-600' : 'text-gray-400'}`}>
              {c.isActive ? '启用' : '禁用'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
