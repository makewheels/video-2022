import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../../src/utils/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));
vi.mock('../../src/utils/auth', () => ({
  getToken: () => 'test-token',
  isLoggedIn: () => true,
  requireAuth: vi.fn(),
}));
vi.mock('../../src/utils/toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));
vi.mock('ali-oss', () => ({
  default: vi.fn(),
}));

import { requireAuth } from '../../src/utils/auth';
import UploadPage from '../../src/pages/UploadPage';

function renderUpload() {
  return render(
    <MemoryRouter initialEntries={['/upload']}>
      <UploadPage />
    </MemoryRouter>,
  );
}

describe('UploadPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', () => {
    renderUpload();
    expect(screen.getByText('上传视频')).toBeDefined();
  });

  it('renders drop zone prompt', () => {
    renderUpload();
    expect(screen.getByText('拖拽文件到此处，或点击选择文件')).toBeDefined();
  });

  it('renders file input element', () => {
    renderUpload();
    const fileInput = document.querySelector('input[type="file"]');
    expect(fileInput).not.toBeNull();
    expect(fileInput?.getAttribute('accept')).toBe('video/*,audio/*');
  });

  it('calls requireAuth on mount', () => {
    renderUpload();
    expect(requireAuth).toHaveBeenCalled();
  });
});
