import { useEffect, useRef } from 'react';
import videojs from 'video.js';
import 'video.js/dist/video-js.css';
import api from '../utils/api';

interface VideoPlayerProps {
  src: string;
  seekTime?: number;
  videoId: string;
  watchId: string;
}

function getClientId(): string {
  let id = localStorage.getItem('clientId');
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem('clientId', id);
  }
  return id;
}

function getSessionId(): string {
  let id = sessionStorage.getItem('sessionId');
  if (!id) {
    id = crypto.randomUUID();
    sessionStorage.setItem('sessionId', id);
  }
  return id;
}

function clampVolume(vol: number): number {
  return Math.min(1, Math.max(0, vol));
}

function initializePlayer(
  container: HTMLDivElement,
  src: string,
  seekTime?: number,
): ReturnType<typeof videojs> {
  const videoEl = document.createElement('video-js');
  videoEl.classList.add('vjs-big-play-centered');
  container.appendChild(videoEl);

  const player = videojs(videoEl, {
    controls: true,
    autoplay: true,
    preload: 'auto',
    fluid: true,
    playsinline: true,
    html5: { vhs: { overrideNative: true } },
  });

  player.src({ src, type: 'application/x-mpegURL' });

  if (seekTime && seekTime > 0) {
    player.on('loadedmetadata', () => {
      player.currentTime(seekTime);
    });
  }

  return player;
}

function setupKeyboardShortcuts(
  player: ReturnType<typeof videojs>,
): (e: KeyboardEvent) => void {
  return (e: KeyboardEvent) => {
    if (player.isDisposed()) return;
    const tag = (e.target as HTMLElement)?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;

    switch (e.key) {
      case ' ':
      case 'k':
        e.preventDefault();
        player.paused() ? player.play() : player.pause();
        break;
      case 'ArrowLeft':
        e.preventDefault();
        player.currentTime(Math.max(0, (player.currentTime() ?? 0) - 5));
        break;
      case 'ArrowRight':
        e.preventDefault();
        player.currentTime((player.currentTime() ?? 0) + 5);
        break;
      case 'j':
        e.preventDefault();
        player.currentTime(Math.max(0, (player.currentTime() ?? 0) - 10));
        break;
      case 'l':
        e.preventDefault();
        player.currentTime((player.currentTime() ?? 0) + 10);
        break;
      case 'f':
        e.preventDefault();
        player.isFullscreen() ? player.exitFullscreen() : player.requestFullscreen();
        break;
      case 'm':
        e.preventDefault();
        player.muted(!player.muted());
        break;
      case 'ArrowUp':
        e.preventDefault();
        player.volume(clampVolume((player.volume() ?? 1) + 0.1));
        break;
      case 'ArrowDown':
        e.preventDefault();
        player.volume(clampVolume((player.volume() ?? 1) - 0.1));
        break;
    }
  };
}

interface HeartbeatHandles {
  heartbeatInterval: ReturnType<typeof setInterval>;
  playbackInterval: ReturnType<typeof setInterval>;
  progressInterval: ReturnType<typeof setInterval>;
  handleVisibilityChange: () => void;
  handlePlay: () => void;
  handlePause: () => void;
  sendPlaybackExit: (exitType: string, preferBeacon?: boolean) => void;
}

function resolveResolution(player: ReturnType<typeof videojs>): string {
  const height = typeof player.videoHeight === 'function' ? player.videoHeight() : 0;
  return height ? `${height}p` : 'auto';
}

function setupHeartbeat(
  player: ReturnType<typeof videojs>,
  videoId: string,
  watchId: string,
  clientId: string,
  sessionId: string,
): HeartbeatHandles {
  let playbackSessionId: string | null = null;
  let totalPlayDurationMs = 0;
  let playStartedAt: number | null = null;
  let exitSent = false;

  const getCurrentTimeMs = () => Math.round((player.currentTime() ?? 0) * 1000);
  const getAccumulatedPlayDuration = () =>
    totalPlayDurationMs + (playStartedAt === null ? 0 : Date.now() - playStartedAt);

  const handlePlay = () => {
    if (playStartedAt === null && !exitSent) {
      playStartedAt = Date.now();
    }
  };

  const handlePause = () => {
    if (playStartedAt !== null) {
      totalPlayDurationMs += Date.now() - playStartedAt;
      playStartedAt = null;
    }
  };

  const heartbeatInterval = setInterval(() => {
    if (player.isDisposed()) return;
    api.post('/heartbeat/add', {
      videoId,
      playerTime: Math.floor(player.currentTime() ?? 0),
      playerStatus: player.paused() ? 'PAUSED' : 'PLAYING',
      clientId, sessionId,
    }).catch(() => {});
  }, 15000);

  api.post('/playback/start', { watchId, videoId, clientId, sessionId })
    .then((res) => {
      playbackSessionId = res.data?.data?.playbackSessionId ?? null;
      if (!player.paused()) {
        handlePlay();
      }
    })
    .catch(() => {});

  const playbackInterval = setInterval(() => {
    if (player.isDisposed() || !playbackSessionId) return;
    api.post('/playback/heartbeat', {
      playbackSessionId,
      currentTimeMs: getCurrentTimeMs(),
      isPlaying: !player.paused(),
      resolution: resolveResolution(player),
      totalPlayDurationMs: getAccumulatedPlayDuration(),
    }).catch(() => {});
  }, 15000);

  const sendPlaybackExit = (exitType: string, preferBeacon = false) => {
    if (exitSent || !playbackSessionId) return;
    handlePause();
    exitSent = true;

    const payload = {
      playbackSessionId,
      currentTimeMs: getCurrentTimeMs(),
      totalPlayDurationMs: getAccumulatedPlayDuration(),
      exitType,
      resolution: resolveResolution(player),
    };

    if (preferBeacon && typeof navigator.sendBeacon === 'function') {
      const data = JSON.stringify(payload);
      navigator.sendBeacon('/playback/exit', new Blob([data], { type: 'application/json' }));
      return;
    }

    api.post('/playback/exit', payload).catch(() => {});
  };

  const handleVisibilityChange = () => {
    if (document.visibilityState === 'hidden') {
      sendPlaybackExit('CLOSE_TAB', true);
    }
  };
  document.addEventListener('visibilitychange', handleVisibilityChange);
  player.on('play', handlePlay);
  player.on('playing', handlePlay);
  player.on('pause', handlePause);

  const progressInterval = setInterval(() => {
    if (player.isDisposed()) return;
    const time = player.currentTime() ?? 0;
    if (time > 0) {
      localStorage.setItem(`video_progress_${videoId}`, String(Math.floor(time)));
    }
  }, 5000);

  return {
    heartbeatInterval,
    playbackInterval,
    progressInterval,
    handleVisibilityChange,
    handlePlay,
    handlePause,
    sendPlaybackExit,
  };
}

export default function VideoPlayer({ src, seekTime, videoId, watchId }: VideoPlayerProps) {
  const videoRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<ReturnType<typeof videojs> | null>(null);

  useEffect(() => {
    if (!videoRef.current) return;

    const player = initializePlayer(videoRef.current, src, seekTime);
    playerRef.current = player;

    const clientId = getClientId();
    const sessionId = getSessionId();
    const heartbeat = setupHeartbeat(player, videoId, watchId, clientId, sessionId);
    const handleKeydown = setupKeyboardShortcuts(player);
    document.addEventListener('keydown', handleKeydown);

    return () => {
      clearInterval(heartbeat.heartbeatInterval);
      clearInterval(heartbeat.playbackInterval);
      clearInterval(heartbeat.progressInterval);
      document.removeEventListener('keydown', handleKeydown);
      document.removeEventListener('visibilitychange', heartbeat.handleVisibilityChange);
      player.off('play', heartbeat.handlePlay);
      player.off('playing', heartbeat.handlePlay);
      player.off('pause', heartbeat.handlePause);
      heartbeat.sendPlaybackExit('NAVIGATE_AWAY');
      if (playerRef.current && !playerRef.current.isDisposed()) {
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  }, [src, seekTime, videoId, watchId]);

  return <div ref={videoRef} data-vjs-player />;
}
