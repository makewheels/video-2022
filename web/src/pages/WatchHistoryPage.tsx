import { useState, useEffect } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import api from '../utils/api';
import { requireAuth } from '../utils/auth';

interface WatchHistoryItem {
  videoId: string;
  title: string;
  coverUrl: string;
  watchTime: string;
}

export default function WatchHistoryPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [items, setItems] = useState<WatchHistoryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const pageSize = 20;
  const page = parseInt(searchParams.get('page') || '0', 10);

  useEffect(() => {
    requireAuth(navigate);
  }, [navigate]);

  useEffect(() => {
    loadHistory();
  }, [page]);

  const loadHistory = async () => {
    setLoading(true);
    try {
      const res = await api.get(`/watchHistory/getMyHistory?page=${page}&pageSize=${pageSize}`);
      const data = res.data.data;
      setItems(data.list || []);
      setTotal(data.total || 0);
    } catch (err) {
      console.error('加载观看历史失败', err);
    } finally {
      setLoading(false);
    }
  };

  const handleClear = async () => {
    if (!window.confirm('确定要清除所有观看历史吗？')) return;
    try {
      await api.delete('/watchHistory/clear');
      setItems([]);
      setTotal(0);
    } catch (err) {
      console.error('清除观看历史失败', err);
    }
  };

  const totalPages = Math.ceil(total / pageSize);

  const formatTime = (timeStr: string) => {
    if (!timeStr) return '';
    const d = new Date(timeStr);
    return d.toLocaleString('zh-CN');
  };

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ margin: 0 }}>观看历史</h2>
        {items.length > 0 && (
          <button
            onClick={handleClear}
            style={{
              padding: '8px 16px',
              background: '#e74c3c',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
            }}
          >
            清除观看历史
          </button>
        )}
      </div>

      {loading ? (
        <p>加载中...</p>
      ) : items.length === 0 ? (
        <p style={{ color: '#999', textAlign: 'center', marginTop: 60 }}>暂无观看历史</p>
      ) : (
        <>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {items.map((item, idx) => (
              <Link
                key={`${item.videoId}-${idx}`}
                to={`/watch/${item.videoId}`}
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <div
                  style={{
                    display: 'flex',
                    gap: 16,
                    padding: 12,
                    borderRadius: 8,
                    border: '1px solid #eee',
                    transition: 'box-shadow 0.2s',
                  }}
                >
                  {item.coverUrl ? (
                    <img
                      src={item.coverUrl}
                      alt={item.title}
                      style={{ width: 160, height: 90, objectFit: 'cover', borderRadius: 4 }}
                    />
                  ) : (
                    <div style={{ width: 160, height: 90, background: '#f0f0f0', borderRadius: 4 }} />
                  )}
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 16, fontWeight: 500, marginBottom: 8 }}>
                      {item.title || '未命名视频'}
                    </div>
                    <div style={{ color: '#999', fontSize: 13 }}>
                      观看时间: {formatTime(item.watchTime)}
                    </div>
                  </div>
                </div>
              </Link>
            ))}
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', gap: 12, marginTop: 24 }}>
              <button
                disabled={page <= 0}
                onClick={() => setSearchParams({ page: String(page - 1) })}
                style={{ padding: '6px 16px' }}
              >
                上一页
              </button>
              <span style={{ lineHeight: '32px' }}>
                {page + 1} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setSearchParams({ page: String(page + 1) })}
                style={{ padding: '6px 16px' }}
              >
                下一页
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
