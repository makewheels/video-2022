import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import type { PlaylistItem } from '../types';
import api from '../utils/api';

interface PlaylistSidebarProps {
  playlistId: string;
  currentVideoId: string;
}

export default function PlaylistSidebar({ playlistId, currentVideoId }: PlaylistSidebarProps) {
  const [items, setItems] = useState<PlaylistItem[]>([]);

  useEffect(() => {
    const load = async () => {
      try {
        const res = await api.get('/playlist/getPlayItemListDetail', {
          params: { playlistId },
        });
        setItems(res.data.data);
      } catch {
        /* ignore */
      }
    };
    load();
  }, [playlistId]);

  return (
    <div className="video-list">
      {items.map((item) => (
        <Link
          key={item.videoId}
          to={`/watch/${item.watchId}?list=${playlistId}`}
          className={`video-item${item.videoId === currentVideoId ? ' active' : ''}`}
        >
          <img className="video-thumbnail" src={item.coverUrl} alt={item.title} />
          <div>
            <div className="video-title">{item.title}</div>
            <div className="video-meta">
              {item.watchCount} 次观看 · {item.videoCreateTime}
            </div>
          </div>
        </Link>
      ))}
    </div>
  );
}
