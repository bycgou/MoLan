import { Routes, Route, Link } from 'react-router-dom';
import { Home } from './pages/Home';
import { NovelList } from './pages/NovelList';
import { NovelDetail } from './pages/NovelDetail';
import { ChapterRead } from './pages/ChapterRead';
import { CharacterList } from './pages/CharacterList';
import { ClueList } from './pages/ClueList';
import { LlmConfigPage } from './pages/LlmConfigPage';
import { StyleCardPage } from './pages/StyleCardPage';

export function App() {
  return (
    <div className="min-h-screen">
      <nav className="bg-white shadow-sm">
        <div className="max-w-6xl mx-auto px-4 py-3 flex gap-4">
          <Link to="/" className="font-bold text-lg">墨韵</Link>
          <Link to="/novels" className="text-gray-600 hover:text-gray-900">小说</Link>
          <Link to="/llm-config" className="text-gray-600 hover:text-gray-900">LLM配置</Link>
          <Link to="/style-cards" className="text-gray-600 hover:text-gray-900">风格卡</Link>
        </div>
      </nav>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/novels" element={<NovelList />} />
        <Route path="/novel/:id" element={<NovelDetail />} />
        <Route path="/novel/:id/chapter/:num" element={<ChapterRead />} />
        <Route path="/novel/:id/characters" element={<CharacterList />} />
        <Route path="/novel/:id/clues" element={<ClueList />} />
        <Route path="/llm-config" element={<LlmConfigPage />} />
        <Route path="/style-cards" element={<StyleCardPage />} />
      </Routes>
    </div>
  );
}
