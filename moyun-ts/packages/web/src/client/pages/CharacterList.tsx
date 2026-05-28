import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useState } from 'react';

export function CharacterList() {
  const { id: novelId } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const { data: chars = [] } = useQuery({ queryKey: ['characters', novelId], queryFn: () => api.listCharacters(novelId!) });
  const [name, setName] = useState('');
  const [role, setRole] = useState('supporting');
  const create = useMutation({
    mutationFn: () => api.createCharacter(novelId!, { name, role }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['characters', novelId] }); setName(''); },
  });

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">角色管理</h1>
      <div className="flex gap-2 mb-6">
        <input value={name} onChange={e => setName(e.target.value)} placeholder="角色名"
          className="flex-1 px-3 py-2 border rounded" />
        <select value={role} onChange={e => setRole(e.target.value)} className="px-3 py-2 border rounded">
          <option value="protagonist">主角</option>
          <option value="supporting">配角</option>
          <option value="villain">反派</option>
          <option value="extra">路人</option>
        </select>
        <button onClick={() => name && create.mutate()} className="px-4 py-2 bg-blue-600 text-white rounded">创建</button>
      </div>
      <div className="space-y-2">
        {chars.map((c: any) => (
          <div key={c.id} className="p-4 bg-white rounded shadow">
            <span className="font-medium">{c.name}</span>
            <span className="ml-2 text-sm text-gray-500">{c.role}</span>
            {c.description && <p className="text-sm text-gray-400 mt-1">{c.description}</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
