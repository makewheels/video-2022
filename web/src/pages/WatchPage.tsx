import { useState, useEffect } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import type { WatchInfo, Video } from '../types';
import api from '../utils/api';
import { getToken } from '../utils/auth';
import VideoPlayer from '../components/VideoPlayer';
import CommentSection from '../components/CommentSection';
import LikeButtons from '../components/LikeButtons';
import ShareButton from '../components/ShareButton';
import PlaylistSidebar from '../components/PlaylistSidebar';
import RecommendedVideos from '../components/RecommendedVideos';

async function ensureClientId(): Promise<string> {
  let clientId = localStorage.getItem('clientId');
  if (!clientId) {
    const res = await api.get('/client/requestClientId');
    clientId = (res.data.data as { clientId: string }).clientId;
    localStorage.setItem('clientId', clientId);
  }
  return clientId;
}

async function ensureSessionId(): Promise<string> {
  let sessionId = sessionStorage.getItem('sessionId');
  if (!sessionId) {
    const res = await api.get('/session/requestSessionId');
    sessionId = (res.data.data as { sessionId: string }).sessionId;
    sessionStorage.setItem('sessionId', sessionId);
  }
  return sessionId;
}

function computeSeekTimeSeconds(tParam: string | null, progressInMillis: number, videoId: string): number {
  if (tParam) {
    const t = Number(tParam);
    if (!isNaN(t)) return t;
  }
  if (progressInMillis > 0) return progressInMillis / 1000;
  const stored = localStorage.getItem(`video_progress_${videoId}`);
  return stored ? parseFloat(stored) : 0;
}

async function loadWatchData(
  watchId: string,
  tParam: string | null,
  setWatchInfo: (w: WatchInfo) => void,
  setVideoDetail: (v: Video) => void,
  setSeekTimeSeconds: (n: number) => void,
  setError: (s: string) => void,
): Promise<void> {
  try {
    const clientId = await ensureClientId();
    const sessionId = await ensureSessionId();
    const token = getToken() || '';

    const watchRes = await api.get('/watchController/getWatchInfo', {
      params: { watchId, clientId, sessionId, token },
    });
    const info = watchRes.data.data as WatchInfo;

    if (info.videoStatus !== 'READY') {
      setError('视频尚未准备好，当前状态：' + info.videoStatus);
      return;
    }

    setWatchInfo(info);
    setSeekTimeSeconds(computeSeekTimeSeconds(tParam, info.progressInMillis, info.videoId));

    const detailRes = await api.get('/video/getVideoDetail', {
      params: { videoId: info.videoId },
    });
    setVideoDetail(detailRes.data.data as Video);

    api.get('/watchController/addWatchLog', {
      params: { videoId: info.videoId, clientId, sessionId, videoStatus: info.videoStatus },
    }).catch(() => {});
  } catch (err) {
    setError(err instanceof Error ? err.message : '加载失败');
  }
}

function WatchPage() {
  const { videoId: watchId } = useParams<{ videoId: string }>();
  const [searchParams] = useSearchParams();
  const listParam = searchParams.get('list');
  const tParam = searchParams.get('t');

  const [watchInfo, setWatchInfo] = useState<WatchInfo | null>(null);
  const [videoDetail, setVideoDetail] = useState<Video | null>(null);
  const [seekTimeSeconds, setSeekTimeSeconds] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!watchId) return;
    setWatchInfo(null);
    setVideoDetail(null);
    setError('');
    loadWatchData(watchId, tParam, setWatchInfo, setVideoDetail, setSeekTimeSeconds, setError);
  }, [watchId, tParam]);

  if (error) return <div className="not-ready-state">{error}</div>;
  if (!watchInfo || !videoDetail) return <div>加载中...</div>;

  return (
    <div className="watch-layout">
      <div className="watch-main">
        <div className="player-wrapper">
          <VideoPlayer
            src={watchInfo.multivariantPlaylistUrl}
            seekTime={seekTimeSeconds}
            videoId={watchInfo.videoId}
            watchId={watchId!}
          />
        </div>
        <div className="video-info-section">
          <h1 className="video-title">{videoDetail.title}</h1>
          <div className="video-meta">
            {videoDetail.watchCount} 次观看 · {videoDetail.createTimeString}
          </div>
          <LikeButtons videoId={watchInfo.videoId} />
          <ShareButton videoId={watchInfo.videoId} />
          <p className="video-description">{videoDetail.description}</p>
        </div>
        <CommentSection videoId={watchInfo.videoId} />
      </div>
      {listParam ? (
        <div className="watch-sidebar">
          <PlaylistSidebar playlistId={listParam} currentVideoId={watchInfo.videoId} />
        </div>
      ) : (
        <div className="watch-sidebar">
          <RecommendedVideos currentVideoId={watchInfo.videoId} />
        </div>
      )}
    </div>
  );
}

export default WatchPage;
