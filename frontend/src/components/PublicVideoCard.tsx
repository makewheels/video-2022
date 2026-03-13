import { Link } from 'react-router-dom';
import type { Video } from '../types';

interface PublicVideoCardProps {
  video: Video;
  compact?: boolean;
}

function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function formatViewCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万次观看`;
  if (count >= 1000) return `${(count / 1000).toFixed(1)}千次观看`;
  return `${count}次观看`;
}

function formatRelativeTime(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  if (months < 12) return `${months}个月前`;
  return `${years}年前`;
}

export default function PublicVideoCard({ video, compact }: PublicVideoCardProps) {
  const uploaderInitial = (video.uploaderName || '?')[0].toUpperCase();

  return (
    <Link to={`/watch/${video.watchId}`} className={`yt-card${compact ? ' yt-card-compact' : ''}`}>
      <div className="yt-card-thumbnail">
        {video.coverUrl ? (
          <img src={video.coverUrl} alt={video.title} loading="lazy" />
        ) : (
          <div className="yt-card-thumbnail-placeholder">▶</div>
        )}
        {video.duration > 0 && (
          <span className="yt-card-duration">{formatDuration(video.duration)}</span>
        )}
      </div>
      <div className="yt-card-info">
        <div className="yt-card-avatar" title={video.uploaderName}>
          {uploaderInitial}
        </div>
        <div className="yt-card-text">
          <h3 className="yt-card-title">{video.title}</h3>
          <div className="yt-card-meta">
            <span className="yt-card-uploader">{video.uploaderName || '未知用户'}</span>
            <span className="yt-card-stats">
              {formatViewCount(video.watchCount)} · {formatRelativeTime(video.createTime)}
            </span>
          </div>
        </div>
      </div>
    </Link>
  );
}
