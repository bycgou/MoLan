import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useState } from 'react';

export function NovelList() {
  const queryClient = useQueryClient();
  const { data: novels = [] } = useQuery({ queryKey: ['novels'], queryFn: api.listNovels });
  const [title, setTitle] = useState('');
  const create = useMutation({
    mutationFn: () => api.createNovel({ title, genre: 'xuanhuan' }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['novels'] }); setTitle(''); },
  });

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">我的小说</h1>
      <div className="flex gap-2 mb-6">
        <input value={title} onChange={e => setTitle(e.target.value)} placeholder="小说标题"
          className="flex-1 px-3 py-2 border rounded" />
        <button onClick={() => title && create.mutate()} className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
          创建
        </button>
      </div>
      <div className="space-y-2">
        {novels.map((n: any) => (
          <Link key={n.id} to={`/novel/${n.id}`}
            className="block p-4 bg-white rounded shadow hover:shadow-md transition">
            <span className="font-medium">{n.title}</span>
            <span className="ml-2 text-sm text-gray-500">{n.genre}</span>
            <span className="ml-2 text-sm text-gray-400">{n.status}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
