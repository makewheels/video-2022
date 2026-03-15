import { render, screen } from '@testing-library/react';
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
vi.mock('../../src/utils/toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

import api from '../../src/utils/api';
import ChannelPage from '../../src/pages/ChannelPage';

function renderChannel(userId = 'test-user') {
  return render(
    <MemoryRouter initialEntries={[`/channel/${userId}`]}>
      <Routes>
        <Route path="/channel/:userId" element={<ChannelPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ChannelPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state', () => {
    vi.mocked(api.get).mockReturnValue(new Promise(() => {}));
    renderChannel();
    expect(screen.getByText('加载中...')).toBeDefined();
  });

  it('stays on loading when fetch fails', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('Network error'));
    renderChannel();
    // Error triggers a toast, page remains in loading state
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.getByText('加载中...')).toBeDefined();
  });
});
