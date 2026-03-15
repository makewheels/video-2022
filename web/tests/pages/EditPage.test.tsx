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
import EditPage from '../../src/pages/EditPage';

function renderEdit(videoId = 'test-id') {
  return render(
    <MemoryRouter initialEntries={[`/edit/${videoId}`]}>
      <Routes>
        <Route path="/edit/:videoId" element={<EditPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('EditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state initially', () => {
    vi.mocked(api.get).mockReturnValue(new Promise(() => {})); // never resolves
    renderEdit();
    expect(screen.getByText('加载中...')).toBeDefined();
  });

  it('renders form with video data', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: {
        code: 0,
        data: {
          id: 'test-id',
          title: '测试视频',
          description: '测试描述',
          visibility: 'PUBLIC',
          watchCount: 10,
          duration: 120,
          createTimeString: '2024-01-01',
          type: 'USER_UPLOAD',
          watchUrl: 'http://example.com/watch/test',
        },
      },
    });
    renderEdit();
    const titleInput = await screen.findByDisplayValue('测试视频');
    expect(titleInput).toBeDefined();
  });

  it('renders delete section', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: {
        code: 0,
        data: {
          id: 'test-id',
          title: '测试视频',
          description: '',
          visibility: 'PUBLIC',
          watchCount: 0,
          duration: 60,
          createTimeString: '2024-01-01',
          type: 'USER_UPLOAD',
          watchUrl: 'http://example.com/watch/test',
        },
      },
    });
    renderEdit();
    const deleteBtn = await screen.findByText('删除视频');
    expect(deleteBtn).toBeDefined();
  });
});
