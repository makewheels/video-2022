import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import type { ChannelInfo, Video } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';
import { isLoggedIn } from '../utils/auth';
import PublicVideoCard from '../components/PublicVideoCard';

const PAGE_SIZE = 20;

export default function ChannelPage() {
  const { userId } = useParams<{ userId: string }>();
  const [channel, setChannel] = useState<ChannelInfo | null>(null);
  const [videos, setVideos] = useState<Video[]>([]);
  const [activeTab, setActiveTab] = useState<'videos' | 'playlists'>('videos');
  const [subscribing, setSubscribing] = useState(false);
  const { toast } = useToast();

  const loadChannel = useCallback(async () => {
    if (!userId) return;
    try {
      const res = await api.get('/user/getChannel', { params: { userId } });
      const data = res.data.data as ChannelInfo;
      if (isLoggedIn()) {
        try {
          const subRes = await api.get('/subscription/getStatus', { params: { channelUserId: userId } });
          data.isSubscribed = subRes.data.data as boolean;
        } catch { /* ignore */ }
      }
      setChannel(data);
    } catch {
      toast('加载频道失败', 'error');
    }
  }, [userId, toast]);

  const loadVideos = useCallback(async () => {
    if (!userId) return;
    try {
      const res = await api.get('/video/getPublicVideoList', {
        params: { skip: 0, limit: PAGE_SIZE },
      });
      const allVideos = (res.data.data as { list: Video[] }).list;
      setVideos(allVideos.filter(v => v.uploaderId === userId));
    } catch { /* ignore */ }
  }, [userId]);

  useEffect(() => {
    loadChannel();
    loadVideos();
  }, [loadChannel, loadVideos]);

  const handleSubscribe = async () => {
    if (!isLoggedIn()) {
      toast('请先登录', 'error');
      return;
    }
    setSubscribing(true);
    try {
      if (channel?.isSubscribed) {
        await api.get('/subscription/unsubscribe', { params: { channelUserId: userId } });
      } else {
        await api.get('/subscription/subscribe', { params: { channelUserId: userId } });
      }
      loadChannel();
    } catch (err) {
      toast(err instanceof Error ? err.message : '操作失败', 'error');
    } finally {
      setSubscribing(false);
    }
  };

  if (!channel) return <div>加载中...</div>;

  return (
    <div className="page-container">
      <div className="channel-banner">
        {channel.bannerUrl ? (
          <img src={channel.bannerUrl} alt="Banner" />
        ) : (
          <div className="channel-banner-placeholder" />
        )}
      </div>

      <div className="channel-info">
        <div className="channel-avatar-large">
          {channel.avatarUrl ? (
            <img src={channel.avatarUrl} alt={channel.nickname || ''} />
          ) : (
            <span>{(channel.nickname || '?')[0].toUpperCase()}</span>
          )}
        </div>
        <div className="channel-details">
          <h1 className="channel-name">{channel.nickname || '未命名频道'}</h1>
          <div className="channel-stats">
            {channel.subscriberCount} 位订阅者 · {channel.videoCount} 个视频
          </div>
          {channel.bio && <p className="channel-bio">{channel.bio}</p>}
        </div>
        <button
          className={`btn ${channel.isSubscribed ? 'btn-secondary' : 'btn-primary'}`}
          onClick={handleSubscribe}
          disabled={subscribing}
        >
          {channel.isSubscribed ? '已订阅' : '订阅'}
        </button>
      </div>

      <div className="channel-tabs">
        <button
          className={`channel-tab${activeTab === 'videos' ? ' active' : ''}`}
          onClick={() => setActiveTab('videos')}
        >
          视频
        </button>
        <button
          className={`channel-tab${activeTab === 'playlists' ? ' active' : ''}`}
          onClick={() => setActiveTab('playlists')}
        >
          播放列表
        </button>
      </div>

      {activeTab === 'videos' && (
        <div className="yt-grid">
          {videos.map((video) => (
            <PublicVideoCard key={video.id} video={video} />
          ))}
          {videos.length === 0 && <div className="empty-state">暂无视频</div>}
        </div>
      )}
      {activeTab === 'playlists' && (
        <div className="empty-state">暂无播放列表</div>
      )}
    </div>
  );
}
