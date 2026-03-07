vi.mock('../../src/utils/api', () => ({
  default: {
    get: vi.fn().mockResolvedValue({
      data: {
        data: {
          list: [
            {
              id: '1',
              watchId: 'w1',
              title: 'Test Video',
              description: '',
              status: 'READY',
              visibility: 'PUBLIC',
              watchCount: 100,
              duration: 120,
              createTimeString: '2024-01-01',
              createTime: '2024-01-01',
              watchUrl: 'http://example.com/w1',
              type: 'USER_UPLOAD',
            },
          ],
          total: 1,
        },
      },
    }),
    post: vi.fn(),
  },
}));

vi.mock('../../src/utils/auth', () => ({
  isLoggedIn: () => true,
  getToken: () => 'test-token',
  requireAuth: () => true,
}));

import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../src/utils/toast';
import MyVideosPage from '../../src/pages/MyVideosPage';

function renderMyVideosPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <MyVideosPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('MyVideosPage', () => {
  it('renders video title', async () => {
    renderMyVideosPage();
    expect(await screen.findByText(/Test Video/)).toBeInTheDocument();
  });

  it('displays total count', async () => {
    renderMyVideosPage();
    expect(await screen.findByText(/共 1 个/)).toBeInTheDocument();
  });

  it('has a search input', () => {
    renderMyVideosPage();
    expect(screen.getByPlaceholderText(/搜索/)).toBeInTheDocument();
  });
});
