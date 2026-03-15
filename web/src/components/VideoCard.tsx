import { Link } from 'react-router-dom';
import type { Video } from '../types';

interface VideoCardProps {
  video: Video;
  onDelete: (videoId: string) => void;
}

function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function formatRelativeTime(dateStr: string): string {
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

export default function VideoCard({ video, onDelete }: VideoCardProps) {
  const visibilityIcon = video.visibility === 'PUBLIC' ? '🔗' : '🔒';
  const isReady = video.status === 'READY';

  return (
    <tr>
      <td>
        {visibilityIcon} {video.title}
        {!isReady && <span className="status-badge">处理中</span>}
      </td>
      <td>{video.watchCount}</td>
      <td>{formatDuration(video.duration)}</td>
      <td>{formatRelativeTime(video.createTime)}</td>
      <td>
        <Link to={`/watch/${video.watchId}`}>播放</Link>
        {' | '}
        <Link to={`/edit/${video.id}`}>编辑</Link>
        {' | '}
        <button className="btn-link" onClick={() => onDelete(video.id)}>删除</button>
      </td>
    </tr>
  );
}
