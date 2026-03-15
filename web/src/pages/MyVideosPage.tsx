import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { Video } from '../types';
import api from '../utils/api';
import { requireAuth } from '../utils/auth';
import { useToast } from '../utils/toast';
import Pagination from '../components/Pagination';
import ConfirmDialog from '../components/ConfirmDialog';
import VideoCard from '../components/VideoCard';

const PAGE_SIZE = 12;

function MyVideosPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { toast } = useToast();

  const currentPage = Number(searchParams.get('page') || '1');
  const keyword = searchParams.get('keyword') || '';

  const [videos, setVideos] = useState<Video[]>([]);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState(keyword);
  const [loading, setLoading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  useEffect(() => {
    requireAuth(navigate);
  }, [navigate]);

  const loadVideos = useCallback(async (page: number, kw: string) => {
    setLoading(true);
    try {
      const skip = (page - 1) * PAGE_SIZE;
      const res = await api.get('/video/getMyVideoList', {
        params: { skip, limit: PAGE_SIZE, keyword: kw },
      });
      const data = res.data.data as { list: Video[]; total: number };
      setVideos(data.list);
      setTotal(data.total);
    } catch {
      toast('加载视频列表失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadVideos(currentPage, keyword);
  }, [currentPage, keyword, loadVideos]);

  useEffect(() => {
    const timer = setTimeout(() => {
      const params: Record<string, string> = {};
      if (searchInput) params.keyword = searchInput;
      setSearchParams(params, { replace: true });
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput, setSearchParams]);

  const handleDelete = (id: string) => {
    setDeleteTarget(id);
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      await api.get('/video/delete', { params: { videoId: deleteTarget } });
      toast('删除成功', 'success');
      setDeleteTarget(null);
      loadVideos(currentPage, keyword);
    } catch {
      toast('删除失败', 'error');
    }
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);

  const handlePageChange = (page: number) => {
    const params: Record<string, string> = { page: String(page) };
    if (keyword) params.keyword = keyword;
    setSearchParams(params);
  };

  return (
    <div className="page-container">
      <div className="section-header">
        <h2 className="section-title">我的视频</h2>
        <span className="video-count">共 {total} 个</span>
      </div>

      <input
        type="text"
        placeholder="搜索视频..."
        value={searchInput}
        onChange={(e) => setSearchInput(e.target.value)}
        className="form-input"
      />

      {loading ? (
        <div className="empty-state">加载中...</div>
      ) : videos.length === 0 ? (
        <div className="empty-state">暂无视频，快去上传吧</div>
      ) : (
        <div className="video-table-wrap">
          <table className="video-table">
            <thead>
              <tr>
                <th>标题</th>
                <th>播放次数</th>
                <th>时长</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {videos.map((v) => (
                <VideoCard key={v.id} video={v} onDelete={handleDelete} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="确认删除"
        message="确定要删除这个视频吗？此操作不可撤销。"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}

export default MyVideosPage;
