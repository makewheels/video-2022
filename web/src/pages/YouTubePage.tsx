import { useState, useCallback } from 'react';
import api from '../utils/api';
import { useToast } from '../utils/toast';

interface CreateResult {
  shortUrl: string;
  watchUrl: string;
}

function YouTubePage() {
  const { toast } = useToast();
  const [url, setUrl] = useState('');
  const [result, setResult] = useState<CreateResult | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = useCallback(async () => {
    const trimmed = url.trim();
    if (!trimmed.includes('youtube.com') && !trimmed.includes('youtu.be')) {
      toast('请输入有效的YouTube链接', 'error');
      return;
    }
    setLoading(true);
    try {
      const res = await api.post('/video/create', {
        videoType: 'YOUTUBE',
        youtubeUrl: trimmed,
      });
      setResult(res.data.data as CreateResult);
      toast('创建成功', 'success');
    } catch (err) {
      toast(err instanceof Error ? err.message : '创建失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [url, toast]);

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
      <div className="card" style={{ width: 500 }}>
        <div className="card-header">搬运YouTube视频</div>
        <div style={{ padding: 24 }}>
          <div className="form-group">
            <input
              className="form-input"
              type="url"
              placeholder="YouTube视频链接"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
            />
          </div>
          <div className="form-group">
            <button
              className="btn btn-primary"
              style={{ width: '100%' }}
              disabled={loading}
              onClick={handleSubmit}
            >
              {loading ? '提交中...' : '提交'}
            </button>
          </div>
          {result && (
            <div style={{ marginTop: 16 }}>
              <p>短链接：<a href={result.shortUrl} target="_blank" rel="noreferrer">{result.shortUrl}</a></p>
              <p>观看链接：<a href={result.watchUrl} target="_blank" rel="noreferrer">{result.watchUrl}</a></p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default YouTubePage;
