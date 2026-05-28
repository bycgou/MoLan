import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';

export function ClueList() {
  const { id: novelId } = useParams<{ id: string }>();
  const { data: clues = [] } = useQuery({ queryKey: ['clues', novelId], queryFn: () => api.listClues(novelId!) });

  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-6">线索管理</h1>
      <div className="space-y-2">
        {clues.map((c: any) => (
          <div key={c.id} className="p-4 bg-white rounded shadow">
            <span className="font-medium">{c.title}</span>
            <span className="ml-2 text-sm text-gray-500">[{c.type}]</span>
            <span className={`ml-2 text-sm ${c.isRecycled ? 'text-gray-400' : 'text-green-600'}`}>
              {c.isRecycled ? '已回收' : '活跃'}
            </span>
            {c.description && <p className="text-sm text-gray-400 mt-1">{c.description}</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
