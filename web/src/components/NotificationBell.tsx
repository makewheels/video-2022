import { useState, useEffect, useRef, useCallback } from 'react';
import api from '../utils/api';
import { isLoggedIn } from '../utils/auth';

interface NotificationItem {
  id: string;
  type: string;
  content: string;
  fromUserId: string;
  toUserId: string;
  relatedVideoId?: string;
  relatedCommentId?: string;
  read: boolean;
  createTime: string;
}

interface NotificationPageVO {
  list: NotificationItem[];
  total: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

function timeAgo(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  return `${days}天前`;
}

function notificationTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    COMMENT_REPLY: '💬 评论回复',
    NEW_SUBSCRIBER: '👤 新订阅',
    VIDEO_LIKE: '👍 视频点赞',
    COMMENT_LIKE: '👍 评论点赞',
  };
  return labels[type] || type;
}

export default function NotificationBell() {
  const [unreadCount, setUnreadCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const fetchUnreadCount = useCallback(async () => {
    if (!isLoggedIn()) return;
    try {
      const res = await api.get('/notification/getUnreadCount');
      setUnreadCount(res.data.data ?? 0);
    } catch { /* ignore */ }
  }, []);

  const fetchNotifications = useCallback(async () => {
    if (!isLoggedIn()) return;
    setLoading(true);
    try {
      const res = await api.get('/notification/getMyNotifications', {
        params: { page: 0, pageSize: 20 },
      });
      const data = res.data.data as NotificationPageVO;
      setNotifications(data?.list ?? []);
    } catch { /* ignore */ }
    setLoading(false);
  }, []);

  useEffect(() => {
    fetchUnreadCount();
    const timer = setInterval(fetchUnreadCount, 30000);
    return () => clearInterval(timer);
  }, [fetchUnreadCount]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleToggle = () => {
    const next = !open;
    setOpen(next);
    if (next) fetchNotifications();
  };

  const handleMarkAsRead = async (id: string) => {
    try {
      await api.post('/notification/markAsRead', { notificationId: id });
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch { /* ignore */ }
  };

  const handleMarkAllAsRead = async () => {
    try {
      await api.post('/notification/markAllAsRead');
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch { /* ignore */ }
  };

  if (!isLoggedIn()) return null;

  return (
    <div className="notification-bell-wrapper" ref={dropdownRef}>
      <button
        className="btn-icon notification-bell-btn"
        type="button"
        title="通知"
        onClick={handleToggle}
      >
        🔔
        {unreadCount > 0 && (
          <span className="notification-badge">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="notification-dropdown">
          <div className="notification-dropdown-header">
            <span className="notification-dropdown-title">通知</span>
            {unreadCount > 0 && (
              <button
                className="btn-link notification-mark-all"
                onClick={handleMarkAllAsRead}
              >
                全部已读
              </button>
            )}
          </div>

          <div className="notification-dropdown-list">
            {loading && (
              <div className="notification-empty">加载中...</div>
            )}
            {!loading && notifications.length === 0 && (
              <div className="notification-empty">暂无通知</div>
            )}
            {!loading &&
              notifications.map((n) => (
                <div
                  key={n.id}
                  className={`notification-item${n.read ? '' : ' unread'}`}
                  onClick={() => !n.read && handleMarkAsRead(n.id)}
                >
                  <div className="notification-item-type">
                    {notificationTypeLabel(n.type)}
                  </div>
                  <div className="notification-item-content">{n.content}</div>
                  <div className="notification-item-time">
                    {timeAgo(n.createTime)}
                  </div>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
