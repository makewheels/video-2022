import { useState, useEffect, useCallback } from 'react';
import type { Comment, CommentPage } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';
import CommentItem from './CommentItem';

interface CommentSectionProps {
  videoId: string;
}

const PAGE_SIZE = 20;

export default function CommentSection({ videoId }: CommentSectionProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [sort, setSort] = useState<'createTime' | 'likeCount'>('createTime');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const { toast } = useToast();

  const loadComments = useCallback(async (page: number, reset: boolean) => {
    try {
      if (!reset) setLoadingMore(true);
      const res = await api.get('/comment/getByVideoId', {
        params: { videoId, page, pageSize: PAGE_SIZE, sortBy: sort },
      });
      const data: CommentPage = res.data.data;
      setComments(reset ? data.list : [...comments, ...data.list]);
      setTotal(data.total);
      setTotalPages(data.totalPages);
      setCurrentPage(data.currentPage);
    } catch {
      toast('加载评论失败', 'error');
    } finally {
      setLoadingMore(false);
    }
  }, [videoId, sort, comments, toast]);

  useEffect(() => {
    loadComments(0, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoId, sort]);

  const handleRefresh = () => {
    loadComments(0, true);
  };

  const handleLoadMore = () => {
    if (currentPage + 1 < totalPages) {
      loadComments(currentPage + 1, false);
    }
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
      <h3>评论 ({total})</h3>

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
          className={`btn btn-sm${sort === 'createTime' ? ' active' : ''}`}
          onClick={() => setSort('createTime')}
        >
          最新
        </button>
        <button
          className={`btn btn-sm${sort === 'likeCount' ? ' active' : ''}`}
          onClick={() => setSort('likeCount')}
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

      {currentPage + 1 < totalPages && (
        <button className="btn btn-secondary" onClick={handleLoadMore} disabled={loadingMore}>
          {loadingMore ? '加载中...' : '加载更多'}
        </button>
      )}
    </div>
  );
}
