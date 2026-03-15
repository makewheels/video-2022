import { useState, useEffect } from 'react';
import type { Video } from '../types';
import api from '../utils/api';
import PublicVideoCard from './PublicVideoCard';

interface RecommendedVideosProps {
  currentVideoId: string;
}

export default function RecommendedVideos({ currentVideoId }: RecommendedVideosProps) {
  const [videos, setVideos] = useState<Video[]>([]);

  useEffect(() => {
    api.get('/video/getPublicVideoList', { params: { skip: 0, limit: 12 } })
      .then((res) => {
        const data = res.data.data as { list: Video[]; total: number };
        setVideos(data.list.filter((v) => v.id !== currentVideoId).slice(0, 10));
      })
      .catch(() => {});
  }, [currentVideoId]);

  if (videos.length === 0) return null;

  return (
    <div className="recommended-videos">
      <h3 className="recommended-title">推荐视频</h3>
      <div className="recommended-list">
        {videos.map((v) => (
          <PublicVideoCard key={v.id} video={v} compact />
        ))}
      </div>
    </div>
  );
}
