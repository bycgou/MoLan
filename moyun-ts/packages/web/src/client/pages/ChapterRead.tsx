import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';

export function ChapterRead() {
  const { id, num } = useParams<{ id: string; num: string }>();
  const { data: ch } = useQuery({
    queryKey: ['chapter', id, num],
    queryFn: () => api.getChapter(id!, parseInt(num!)),
  });

  if (!ch) return <div className="p-8">加载中...</div>;

  return (
    <div className="max-w-3xl mx-auto p-8">
      <h1 className="text-2xl font-bold mb-2">第{ch.chapterNum}章 {ch.title ?? ''}</h1>
      <p className="text-gray-500 mb-6">{ch.wordCount}字</p>
      <div className="bg-white rounded shadow p-6 whitespace-pre-wrap leading-relaxed">
        {ch.content ?? '(章节内容为空)'}
      </div>
    </div>
  );
}
