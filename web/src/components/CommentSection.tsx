import { useState, useEffect, useCallback } from 'react';
import type { Comment } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';
import CommentItem from './CommentItem';

interface CommentSectionProps {
  videoId: string;
}

const LIMIT = 20;

export default function CommentSection({ videoId }: CommentSectionProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [count, setCount] = useState(0);
  const [sort, setSort] = useState<'time' | 'hot'>('time');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { toast } = useToast();

  const loadComments = useCallback(async (reset = true) => {
    try {
      const skip = reset ? 0 : comments.length;
      const res = await api.get('/comment/getByVideoId', {
        params: { videoId, skip, limit: LIMIT, sort },
      });
      const data: Comment[] = res.data.data;
      setComments(reset ? data : [...comments, ...data]);
    } catch {
      toast('加载评论失败', 'error');
    }
  }, [videoId, sort, comments, toast]);

  const loadCount = useCallback(async () => {
    try {
      const res = await api.get('/comment/getCount', { params: { videoId } });
      setCount(res.data.data.count);
    } catch {
      /* ignore */
    }
  }, [videoId]);

  useEffect(() => {
    loadComments(true);
    loadCount();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoId, sort]);

  const handleRefresh = () => {
    loadComments(true);
    loadCount();
  };

  const handleSubmit = async () => {
    if (!content.trim()) return;
    setSubmitting(true);
    try {
      await api.post('/comment/add', { videoId, content: content.trim() });
      setContent('');
      handleRefresh();
      toast('评论成功', 'success');
    } catch {
      toast('评论失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="comment-section">
      <h3>评论 ({count})</h3>

      <div className="comment-input">
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="写下你的评论..."
          rows={3}
        />
        <div className="comment-input-actions">
          <button className="btn btn-secondary" onClick={() => setContent('')}>取消</button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting || !content.trim()}>
            {submitting ? '提交中...' : '提交'}
          </button>
        </div>
      </div>

      <div className="comment-sort">
        <button
          className={`btn btn-sm${sort === 'time' ? ' active' : ''}`}
          onClick={() => setSort('time')}
        >
          最新
        </button>
        <button
          className={`btn btn-sm${sort === 'hot' ? ' active' : ''}`}
          onClick={() => setSort('hot')}
        >
          最热
        </button>
      </div>

      <div className="comment-list">
        {comments.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            videoId={videoId}
            onRefresh={handleRefresh}
          />
        ))}
      </div>

      {comments.length >= LIMIT && (
        <button className="btn btn-secondary" onClick={() => loadComments(false)}>
          加载更多
        </button>
      )}
    </div>
  );
}
