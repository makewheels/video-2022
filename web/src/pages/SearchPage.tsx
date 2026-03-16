import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Video } from '../types';
import api from '../utils/api';
import Pagination from '../components/Pagination';
import PublicVideoCard from '../components/PublicVideoCard';
import SearchBar from '../components/SearchBar';

const PAGE_SIZE = 20;
const CATEGORIES = [
  '音乐', '游戏', '教育', '科技', '生活',
  '娱乐', '新闻', '体育', '动漫', '美食',
  '旅行', '知识', '影视', '搞笑', '其他',
];

interface SearchResult {
  content: Video[];
  total: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') || '';
  const category = searchParams.get('category') || '';
  const currentPage = Number(searchParams.get('page') || '0');

  const [result, setResult] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(false);

  const doSearch = useCallback(async (q: string, cat: string, page: number) => {
    setLoading(true);
    try {
      const params: Record<string, string | number> = { page, pageSize: PAGE_SIZE };
      if (q) params.q = q;
      if (cat) params.category = cat;
      const res = await api.get('/search', { params });
      setResult(res.data.data as SearchResult);
    } catch {
      setResult(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    doSearch(query, category, currentPage);
  }, [query, category, currentPage, doSearch]);

  const updateParams = (updates: Record<string, string>) => {
    const params: Record<string, string> = {};
    if (query) params.q = query;
    if (category) params.category = category;
    Object.assign(params, updates);
    setSearchParams(params);
  };

  const handleSearch = (q: string) => {
    updateParams({ q, page: '0' });
  };

  const handleCategoryChange = (cat: string) => {
    const params: Record<string, string> = { page: '0' };
    if (query) params.q = query;
    if (cat) params.category = cat;
    setSearchParams(params);
  };

  const handlePageChange = (page: number) => {
    const params: Record<string, string> = { page: String(page) };
    if (query) params.q = query;
    if (category) params.category = category;
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const displayPage = currentPage + 1;
  const totalDisplayPages = result?.totalPages || 0;

  return (
    <div className="page-container search-page">
      <div className="search-page-header">
        <SearchBar initialQuery={query} onSearch={handleSearch} />
        <div className="search-category-filter">
          <button
            className={`category-chip${!category ? ' active' : ''}`}
            onClick={() => handleCategoryChange('')}
          >
            全部
          </button>
          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              className={`category-chip${category === cat ? ' active' : ''}`}
              onClick={() => handleCategoryChange(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="empty-state">搜索中...</div>
      ) : !result || result.content.length === 0 ? (
        <div className="empty-state">
          {query ? `未找到与"${query}"相关的视频` : '请输入搜索关键词'}
        </div>
      ) : (
        <>
          <div className="search-result-info">
            共找到 {result.total} 个结果
          </div>
          <div className="video-grid">
            {result.content.map((v) => (
              <PublicVideoCard key={v.id} video={v} />
            ))}
          </div>
        </>
      )}

      {totalDisplayPages > 1 && (
        <Pagination
          currentPage={displayPage}
          totalPages={totalDisplayPages}
          onPageChange={(p) => handlePageChange(p - 1)}
        />
      )}
    </div>
  );
}

export default SearchPage;
