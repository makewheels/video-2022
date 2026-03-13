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

function ReplyInput({
  videoId,
  parentCommentId,
  onClose,
  onRefresh,
}: {
  videoId: string;
  parentCommentId: string;
  onClose: () => void;
  onRefresh: () => void;
}) {
  const [replyContent, setReplyContent] = useState('');
  const { toast } = useToast();

  const handleReplySubmit = async () => {
    if (!replyContent.trim()) return;
    try {
      await api.post('/comment/add', {
        videoId,
        content: replyContent.trim(),
        parentId: parentCommentId,
      });
      setReplyContent('');
      onClose();
      onRefresh();
    } catch {
      toast('回复失败', 'error');
    }
  };

  return (
    <div className="reply-input">
      <textarea
        value={replyContent}
        onChange={(e) => setReplyContent(e.target.value)}
        placeholder="写下你的回复..."
        rows={2}
      />
      <div className="reply-input-actions">
        <button className="btn btn-secondary btn-sm" onClick={onClose}>取消</button>
        <button className="btn btn-primary btn-sm" onClick={handleReplySubmit}>回复</button>
      </div>
    </div>
  );
}

function RepliesSection({
  comment,
  videoId,
  onRefresh,
}: {
  comment: Comment;
  videoId: string;
  onRefresh: () => void;
}) {
  const [showReplies, setShowReplies] = useState(false);
  const [replies, setReplies] = useState<Comment[]>([]);
  const { toast } = useToast();

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
    <div className="comment-replies-toggle">
      {showReplies ? (
        <button className="btn-link" onClick={() => setShowReplies(false)}>收起回复</button>
      ) : (
        <button className="btn-link" onClick={loadReplies}>查看 {comment.replyCount} 条回复</button>
      )}
      {showReplies && (
        <div className="comment-replies">
          {replies.map((reply) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              videoId={videoId}
              isReply
              onRefresh={() => { onRefresh(); loadReplies(); }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function CommentItem({ comment, videoId, isReply, onRefresh }: CommentItemProps) {
  const [showReplyInput, setShowReplyInput] = useState(false);
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

  const displayName = comment.userNickname || maskPhone(comment.userPhone);
  const displayInitial = displayName[0].toUpperCase();

  return (
    <div className={`comment-item${isReply ? ' comment-reply' : ''}`}>
      <div style={{ display: 'flex', gap: '0.75rem' }}>
        <div className="comment-avatar">
          {comment.userAvatarUrl ? <img src={comment.userAvatarUrl} alt="" /> : displayInitial}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="comment-header">
            <span className="comment-user">{displayName}</span>
            <span className="comment-time">{timeAgo(comment.createTime)}</span>
          </div>
          <div className="comment-content">
            {isReply && comment.replyToUserPhone && (
              <span className="comment-reply-to">@{comment.replyToUserNickname || maskPhone(comment.replyToUserPhone)} </span>
            )}
            {comment.content}
          </div>
          <div className="comment-actions">
            <button className="btn-link" onClick={handleLike}>👍 {comment.likeCount > 0 ? comment.likeCount : ''}</button>
            <button className="btn-link" onClick={() => setShowReplyInput(!showReplyInput)}>回复</button>
            <button className="btn-link" onClick={handleDelete}>删除</button>
          </div>
          {showReplyInput && (
            <ReplyInput videoId={videoId} parentCommentId={comment.id} onClose={() => setShowReplyInput(false)} onRefresh={onRefresh} />
          )}
          {!isReply && comment.replyCount > 0 && (
            <RepliesSection comment={comment} videoId={videoId} onRefresh={onRefresh} />
          )}
        </div>
      </div>
    </div>
  );
}
