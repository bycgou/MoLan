import { Link } from 'react-router-dom';

export function Home() {
  return (
    <div className="max-w-4xl mx-auto p-8">
      <h1 className="text-4xl font-bold mb-4">墨韵 AI</h1>
      <p className="text-gray-600 mb-8">AI驱动的长篇小说创作系统</p>
      <div className="grid grid-cols-3 gap-4">
        <Link to="/novels" className="p-6 bg-white rounded-lg shadow hover:shadow-md transition">
          <h2 className="text-xl font-semibold mb-2">我的小说</h2>
          <p className="text-gray-500">管理所有小说</p>
        </Link>
        <Link to="/llm-config" className="p-6 bg-white rounded-lg shadow hover:shadow-md transition">
          <h2 className="text-xl font-semibold mb-2">LLM配置</h2>
          <p className="text-gray-500">管理AI模型</p>
        </Link>
        <Link to="/style-cards" className="p-6 bg-white rounded-lg shadow hover:shadow-md transition">
          <h2 className="text-xl font-semibold mb-2">风格卡</h2>
          <p className="text-gray-500">管理写作风格</p>
        </Link>
      </div>
    </div>
  );
}
