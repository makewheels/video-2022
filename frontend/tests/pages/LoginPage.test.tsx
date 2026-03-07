vi.mock('../../src/utils/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}));

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../src/utils/toast';
import LoginPage from '../../src/pages/LoginPage';

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <LoginPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  it('renders the 获取验证码 button', () => {
    renderLoginPage();
    expect(screen.getByText('获取验证码')).toBeInTheDocument();
  });

  it('renders the 登录 button', () => {
    renderLoginPage();
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
  });

  it('shows error toast for invalid phone number', async () => {
    renderLoginPage();
    const phoneInput = screen.getByPlaceholderText(/手机号/);
    await userEvent.type(phoneInput, '123');
    await userEvent.click(screen.getByText('获取验证码'));
    expect(await screen.findByText(/手机号/)).toBeInTheDocument();
  });
});
