import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../../src/utils/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock('../../src/utils/auth', () => ({
  getToken: () => 'test-token',
  isLoggedIn: () => true,
  requireAuth: () => true,
}));
vi.mock('../../src/components/VideoPlayer', () => ({
  default: () => <div>VideoPlayer</div>,
}));
vi.mock('../../src/components/CommentSection', () => ({
  default: () => <div>CommentSection</div>,
}));
vi.mock('../../src/components/LikeButtons', () => ({
  default: () => <div>LikeButtons</div>,
}));
vi.mock('../../src/components/PlaylistSidebar', () => ({
  default: () => <div>PlaylistSidebar</div>,
}));
vi.mock('../../src/components/RecommendedVideos', () => ({
  default: () => <div>RecommendedVideos</div>,
}));

import api from '../../src/utils/api';
import WatchPage from '../../src/pages/WatchPage';

function renderWatch(watchId = 'w1', search = '') {
  return render(
    <MemoryRouter initialEntries={[`/watch/${watchId}${search}`]}>
      <Routes>
        <Route path="/watch/:videoId" element={<WatchPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('WatchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem('clientId', 'test-client-id');
    sessionStorage.setItem('sessionId', 'test-session-id');
  });

  it('shows loading state', () => {
    vi.mocked(api.get).mockReturnValue(new Promise(() => {}));
    renderWatch();
    expect(screen.getByText('加载中...')).toBeDefined();
  });

  it('renders video info after loading', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/watchController/getWatchInfo') {
        return Promise.resolve({
          data: {
            data: {
              videoId: 'v1',
              coverUrl: '',
              videoStatus: 'READY',
              multivariantPlaylistUrl: 'http://example.com/playlist.m3u8',
              progressInMillis: 0,
            },
          },
        });
      }
      if (url === '/video/getVideoDetail') {
        return Promise.resolve({
          data: {
            data: {
              id: 'v1',
              title: '测试视频标题',
              description: '测试描述',
              watchCount: 42,
              createTimeString: '2024-06-01',
            },
          },
        });
      }
      return Promise.resolve({ data: { data: {} } });
    });

    renderWatch();
    const title = await screen.findByText('测试视频标题');
    expect(title).toBeDefined();
    expect(screen.getByText('42 次观看 · 2024-06-01')).toBeDefined();
  });

  it('shows error when video is not ready', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/watchController/getWatchInfo') {
        return Promise.resolve({
          data: {
            data: {
              videoId: 'v1',
              coverUrl: '',
              videoStatus: 'TRANSCODING',
              multivariantPlaylistUrl: '',
              progressInMillis: 0,
            },
          },
        });
      }
      return Promise.resolve({ data: { data: {} } });
    });

    renderWatch();
    await waitFor(() => {
      expect(screen.getByText('视频尚未准备好，当前状态：TRANSCODING')).toBeDefined();
    });
  });

  it('shows error on network failure', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('Network error'));

    renderWatch();
    await waitFor(() => {
      expect(screen.getByText('Network error')).toBeDefined();
    });
  });
});
