import { useState, useEffect } from 'react';
import type { LikeStatus } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';

interface LikeButtonsProps {
  videoId: string;
}

export default function LikeButtons({ videoId }: LikeButtonsProps) {
  const [status, setStatus] = useState<LikeStatus>({ likeCount: 0, userAction: 'NONE' });
  const { toast } = useToast();

  const loadStatus = async () => {
    try {
      const res = await api.get('/videoLike/getStatus', { params: { videoId } });
      setStatus(res.data.data);
    } catch {
      /* ignore */
    }
  };

  useEffect(() => {
    loadStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoId]);

  const handleLike = async () => {
    try {
      await api.get('/videoLike/like', { params: { videoId } });
      loadStatus();
    } catch {
      toast('操作失败', 'error');
    }
  };

  const handleDislike = async () => {
    try {
      await api.get('/videoLike/dislike', { params: { videoId } });
      loadStatus();
    } catch {
      toast('操作失败', 'error');
    }
  };

  const handleShare = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast('链接已复制', 'success');
    } catch {
      toast('复制失败', 'error');
    }
  };

  const likeActive = status.userAction === 'LIKE';
  const dislikeActive = status.userAction === 'DISLIKE';

  return (
    <div className="like-buttons">
      <button
        className="btn btn-secondary"
        style={likeActive ? { fontWeight: 'bold', opacity: 1 } : { opacity: 0.6 }}
        onClick={handleLike}
      >
        👍 {status.likeCount > 0 ? status.likeCount : ''}
      </button>
      <button
        className="btn btn-secondary"
        style={dislikeActive ? { fontWeight: 'bold', opacity: 1 } : { opacity: 0.6 }}
        onClick={handleDislike}
      >
        👎
      </button>
      <button className="btn btn-secondary" onClick={handleShare}>
        🔗 分享
      </button>
    </div>
  );
}
