import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useState } from 'react';

export function StyleCardPage() {
  const qc = useQueryClient();
  const { data: cards = [] } = useQuery({ queryKey: ['style-cards'], queryFn: api.listStyleCards });
  const [name, setName] = useState('');
  const create = useMutation({
    mutationFn: () => api.createStyleCard({ name }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['style-cards'] }); setName(''); },
  });

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">风格卡库</h1>
      <div className="flex gap-2 mb-6">
        <input value={name} onChange={e => setName(e.target.value)} placeholder="风格卡名称"
          className="flex-1 px-3 py-2 border rounded" />
        <button onClick={() => name && create.mutate()} className="px-4 py-2 bg-blue-600 text-white rounded">创建</button>
      </div>
      <div className="space-y-2">
        {cards.map((c: any) => (
          <div key={c.id} className="p-4 bg-white rounded shadow">
            <span className="font-medium">{c.name}</span>
            {c.sampleText && <p className="text-sm text-gray-400 mt-1">{c.sampleText.substring(0, 100)}...</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
