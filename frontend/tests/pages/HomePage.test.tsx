import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../../src/utils/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

import api from '../../src/utils/api';
import HomePage from '../../src/pages/HomePage';

function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <HomePage />
    </MemoryRouter>,
  );
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty state when no videos', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { code: 0, data: { list: [], total: 0 } },
    });
    renderHome();
    await waitFor(() => {
      expect(screen.getByText('暂无公开视频')).toBeDefined();
    });
  });

  it('renders video cards when data loaded', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            {
              id: 'v1',
              title: '视频一',
              coverUrl: '',
              watchCount: 5,
              duration: 60,
              createTimeString: '2024-01-01',
              uploaderName: '用户1',
            },
          ],
          total: 1,
        },
      },
    });
    renderHome();
    const title = await screen.findByText('视频一');
    expect(title).toBeDefined();
  });

  it('shows loading state', () => {
    vi.mocked(api.get).mockReturnValue(new Promise(() => {}));
    renderHome();
    expect(screen.getByText('加载中...')).toBeDefined();
  });
});
