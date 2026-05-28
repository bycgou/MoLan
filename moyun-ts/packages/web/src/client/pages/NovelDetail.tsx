import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';

export function NovelDetail() {
  const { id } = useParams<{ id: string }>();
  const { data: novel } = useQuery({ queryKey: ['novel', id], queryFn: () => api.getNovel(id!) });
  const { data: chapters = [] } = useQuery({ queryKey: ['chapters', id], queryFn: () => api.listChapters(id!) });
  const { data: characters = [] } = useQuery({ queryKey: ['characters', id], queryFn: () => api.listCharacters(id!) });
  const { data: clues = [] } = useQuery({ queryKey: ['clues', id], queryFn: () => api.listClues(id!) });

  if (!novel) return <div className="p-8">加载中...</div>;

  return (
    <div className="max-w-6xl mx-auto p-8">
      <h1 className="text-3xl font-bold mb-2">{novel.title}</h1>
      <p className="text-gray-500 mb-6">{novel.genre} · {novel.status}</p>

      <div className="grid grid-cols-4 gap-4 mb-8">
        <Link to={`/novel/${id}/characters`} className="p-4 bg-white rounded shadow text-center">
          <div className="text-2xl font-bold">{characters.length}</div>
          <div className="text-gray-500">角色</div>
        </Link>
        <Link to={`/novel/${id}/clues`} className="p-4 bg-white rounded shadow text-center">
          <div className="text-2xl font-bold">{clues.length}</div>
          <div className="text-gray-500">线索</div>
        </Link>
        <div className="p-4 bg-white rounded shadow text-center">
          <div className="text-2xl font-bold">{chapters.length}</div>
          <div className="text-gray-500">章节</div>
        </div>
        <div className="p-4 bg-white rounded shadow text-center">
          <div className="text-2xl font-bold">{novel.totalOutline ? '✓' : '✗'}</div>
          <div className="text-gray-500">大纲</div>
        </div>
      </div>

      <h2 className="text-xl font-semibold mb-4">章节列表</h2>
      <div className="space-y-2">
        {chapters.map((ch: any) => (
          <Link key={ch.id} to={`/novel/${id}/chapter/${ch.chapterNum}`}
            className="block p-3 bg-white rounded shadow hover:shadow-md transition">
            第{ch.chapterNum}章 {ch.title ?? '(无标题)'}
            <span className="ml-2 text-sm text-gray-400">{ch.wordCount}字</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
