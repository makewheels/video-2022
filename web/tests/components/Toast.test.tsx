import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider, useToast } from '../../src/utils/toast';

function TestComponent({ message, type }: { message: string; type: 'info' | 'success' | 'error' }) {
  const { toast } = useToast();
  return <button onClick={() => toast(message, type)}>trigger</button>;
}

describe('Toast', () => {
  it('renders success toast with correct class', async () => {
    render(
      <ToastProvider>
        <TestComponent message="Success!" type="success" />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByText('trigger'));
    const toastEl = screen.getByText('Success!');
    expect(toastEl).toBeInTheDocument();
    expect(toastEl).toHaveClass('toast-success');
  });

  it('renders error toast with correct class', async () => {
    render(
      <ToastProvider>
        <TestComponent message="Error!" type="error" />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByText('trigger'));
    const toastEl = screen.getByText('Error!');
    expect(toastEl).toBeInTheDocument();
    expect(toastEl).toHaveClass('toast-error');
  });

  it('renders info toast with correct class', async () => {
    render(
      <ToastProvider>
        <TestComponent message="Info!" type="info" />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByText('trigger'));
    const toastEl = screen.getByText('Info!');
    expect(toastEl).toBeInTheDocument();
    expect(toastEl).toHaveClass('toast-info');
  });
});
