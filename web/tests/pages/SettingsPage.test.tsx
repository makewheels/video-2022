import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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
import SettingsPage from '../../src/pages/SettingsPage';

function renderSettings() {
  return render(
    <MemoryRouter initialEntries={['/settings']}>
      <SettingsPage />
    </MemoryRouter>,
  );
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockResolvedValue({
      data: { code: 0, data: { nickname: '', bio: '', avatarUrl: '' } },
    });
  });

  it('renders settings form', async () => {
    renderSettings();
    await waitFor(() => {
      expect(screen.getByText('个人设置')).toBeDefined();
    });
    expect(screen.getByPlaceholderText('输入昵称')).toBeDefined();
    expect(screen.getByText('保存')).toBeDefined();
  });

  it('shows bio character count', async () => {
    renderSettings();
    await waitFor(() => {
      expect(screen.getByText(/\/200/)).toBeDefined();
    });
  });
});
