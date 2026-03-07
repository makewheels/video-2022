import { useState } from 'react';
import type { Comment } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';

interface CommentItemProps {
  comment: Comment;
  videoId: string;
  isReply?: boolean;
  onRefresh: () => void;
}

function maskPhone(phone: string): string {
  if (phone.length < 7) return phone;
  return phone.slice(0, 3) + '****' + phone.slice(-4);
}

function timeAgo(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (seconds < 60) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  return `${days}天前`;
}

export default function CommentItem({ comment, videoId, isReply, onRefresh }: CommentItemProps) {
  const [showReplyInput, setShowReplyInput] = useState(false);
  const [replyContent, setReplyContent] = useState('');
  const [showReplies, setShowReplies] = useState(false);
  const [replies, setReplies] = useState<Comment[]>([]);
  const { toast } = useToast();

  const handleLike = async () => {
    try {
      await api.get('/comment/like', { params: { commentId: comment.id } });
      onRefresh();
    } catch {
      toast('操作失败', 'error');
    }
  };

  const handleDelete = async () => {
    if (!confirm('确定删除此评论？')) return;
    try {
      await api.get('/comment/delete', { params: { commentId: comment.id } });
      onRefresh();
    } catch {
      toast('删除失败', 'error');
    }
  };

  const handleReplySubmit = async () => {
    if (!replyContent.trim()) return;
    try {
      await api.post('/comment/add', {
        videoId,
        content: replyContent.trim(),
        parentId: comment.id,
      });
      setReplyContent('');
      setShowReplyInput(false);
      onRefresh();
    } catch {
      toast('回复失败', 'error');
    }
  };

  const loadReplies = async () => {
    try {
      const res = await api.get('/comment/getReplies', {
        params: { parentId: comment.id, skip: 0, limit: 50 },
      });
      setReplies(res.data.data);
      setShowReplies(true);
    } catch {
      toast('加载回复失败', 'error');
    }
  };

  return (
    <div className={`comment-item${isReply ? ' comment-reply' : ''}`}>
      <div className="comment-header">
        <span className="comment-user">{maskPhone(comment.userPhone)}</span>
        <span className="comment-time">{timeAgo(comment.createTime)}</span>
      </div>

      <div className="comment-content">
        {isReply && comment.replyToUserPhone && (
          <span className="comment-reply-to">@{maskPhone(comment.replyToUserPhone)} </span>
        )}
        {comment.content}
      </div>

      <div className="comment-actions">
        <button className="btn-link" onClick={handleLike}>
          👍 {comment.likeCount > 0 ? comment.likeCount : ''}
        </button>
        <button className="btn-link" onClick={() => setShowReplyInput(!showReplyInput)}>
          回复
        </button>
        <button className="btn-link" onClick={handleDelete}>
          删除
        </button>
      </div>

      {showReplyInput && (
        <div className="reply-input">
          <textarea
            value={replyContent}
            onChange={(e) => setReplyContent(e.target.value)}
            placeholder="写下你的回复..."
            rows={2}
          />
          <div className="reply-input-actions">
            <button className="btn btn-secondary btn-sm" onClick={() => setShowReplyInput(false)}>
              取消
            </button>
            <button className="btn btn-primary btn-sm" onClick={handleReplySubmit}>
              回复
            </button>
          </div>
        </div>
      )}

      {!isReply && comment.replyCount > 0 && (
        <div className="comment-replies-toggle">
          {showReplies ? (
            <button className="btn-link" onClick={() => setShowReplies(false)}>
              收起回复
            </button>
          ) : (
            <button className="btn-link" onClick={loadReplies}>
              查看 {comment.replyCount} 条回复
            </button>
          )}
          {showReplies && (
            <div className="comment-replies">
              {replies.map((reply) => (
                <CommentItem
                  key={reply.id}
                  comment={reply}
                  videoId={videoId}
                  isReply
                  onRefresh={() => {
                    onRefresh();
                    loadReplies();
                  }}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
