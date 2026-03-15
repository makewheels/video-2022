import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../../src/utils/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

const mockToast = vi.fn();
vi.mock('../../src/utils/toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

import api from '../../src/utils/api';
import YouTubePage from '../../src/pages/YouTubePage';

function renderYouTube() {
  return render(
    <MemoryRouter initialEntries={['/youtube']}>
      <YouTubePage />
    </MemoryRouter>,
  );
}

describe('YouTubePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders form elements', () => {
    renderYouTube();
    expect(screen.getByText('搬运YouTube视频')).toBeDefined();
    expect(screen.getByPlaceholderText('YouTube视频链接')).toBeDefined();
    expect(screen.getByText('提交')).toBeDefined();
  });

  it('shows validation error for invalid URL', async () => {
    const user = userEvent.setup();
    renderYouTube();

    const input = screen.getByPlaceholderText('YouTube视频链接');
    await user.type(input, 'not-a-youtube-url');
    await user.click(screen.getByText('提交'));

    expect(mockToast).toHaveBeenCalledWith('请输入有效的YouTube链接', 'error');
  });

  it('shows result links after successful submission', async () => {
    const user = userEvent.setup();
    vi.mocked(api.post).mockResolvedValue({
      data: {
        data: {
          shortUrl: 'https://short.link/abc',
          watchUrl: 'https://example.com/watch/xyz',
        },
      },
    });

    renderYouTube();
    const input = screen.getByPlaceholderText('YouTube视频链接');
    await user.type(input, 'https://www.youtube.com/watch?v=test123');
    await user.click(screen.getByText('提交'));

    await waitFor(() => {
      expect(screen.getByText('https://short.link/abc')).toBeDefined();
      expect(screen.getByText('https://example.com/watch/xyz')).toBeDefined();
    });
    expect(mockToast).toHaveBeenCalledWith('创建成功', 'success');
  });

  it('shows error toast on API failure', async () => {
    const user = userEvent.setup();
    vi.mocked(api.post).mockRejectedValue(new Error('Server error'));

    renderYouTube();
    const input = screen.getByPlaceholderText('YouTube视频链接');
    await user.type(input, 'https://youtu.be/test123');
    await user.click(screen.getByText('提交'));

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith('Server error', 'error');
    });
  });
});
