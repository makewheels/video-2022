import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Video } from '../types';
import api from '../utils/api';
import Pagination from '../components/Pagination';
import PublicVideoCard from '../components/PublicVideoCard';

const PAGE_SIZE = 24;

function HomePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentPage = Number(searchParams.get('page') || '1');
  const keyword = searchParams.get('keyword') || '';

  const [videos, setVideos] = useState<Video[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const loadVideos = useCallback(async (page: number, kw: string) => {
    setLoading(true);
    try {
      const skip = (page - 1) * PAGE_SIZE;
      const params: Record<string, string | number> = { skip, limit: PAGE_SIZE };
      if (kw) params.keyword = kw;
      const res = await api.get('/video/getPublicVideoList', { params });
      const data = res.data.data as { list: Video[]; total: number };
      setVideos(data.list);
      setTotal(data.total);
    } catch {
      setVideos([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadVideos(currentPage, keyword);
  }, [currentPage, keyword, loadVideos]);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const handlePageChange = (page: number) => {
    const params: Record<string, string> = { page: String(page) };
    if (keyword) params.keyword = keyword;
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="page-container home-page">
      {loading ? (
        <div className="empty-state">加载中...</div>
      ) : videos.length === 0 ? (
        <div className="empty-state">
          {keyword ? `未找到与"${keyword}"相关的视频` : '暂无公开视频'}
        </div>
      ) : (
        <div className="video-grid">
          {videos.map((v) => (
            <PublicVideoCard key={v.id} video={v} />
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      )}
    </div>
  );
}

export default HomePage;
