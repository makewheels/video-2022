import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);
Element.prototype.scrollIntoView = vi.fn();

import { ToastProvider } from '../../src/utils/toast';
import ChatPage from '../../src/pages/ChatPage';

const HEALTH_OK = { status: 'ok', backend: 'fixture', model: 'MiniMax-M2.7', tools_count: 38 };

function renderChat() {
  return render(
    <MemoryRouter initialEntries={['/chat']}>
      <ToastProvider>
        <ChatPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

function sseChunks(...events: (Record<string, unknown> | null)[]) {
  const lines = events.flatMap(e => {
    if (e === null) return ['data: [DONE]'];
    return [`data: ${JSON.stringify(e)}`];
  }).join('\n') + '\n';
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(lines));
      controller.close();
    },
  });
  return Promise.resolve({ ok: true, body: stream });
}

function mockHealth(data: Record<string, unknown> = HEALTH_OK) {
  mockFetch.mockImplementation((url: string, opts?: RequestInit) => {
    if (url === '/agent-api/health') {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(data) });
    }
    // for chat/stream, return from this test's own setup or reject
    const body = opts?.body as string | undefined;
    if (url === '/agent-api/chat/stream' && body) {
      return Promise.reject(new Error('mock not set up for stream in this test'));
    }
    return Promise.reject(new Error('unexpected fetch'));
  });
}

describe('ChatPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHealth();
  });

  it('renders welcome screen with quick actions', async () => {
    renderChat();
    const title = document.querySelector('.chat-title');
    expect(title?.textContent).toContain('AI 视频助手');
    expect(screen.getByText('我上传了几个视频？')).toBeDefined();
    expect(screen.getByText('哪个视频播放量最高？')).toBeDefined();
  });

  it('shows server online status after health check', async () => {
    renderChat();
    await screen.findByText(/MiniMax-M2\.7/);
  });

  it('shows offline when health check fails', async () => {
    mockFetch.mockImplementation(() => Promise.reject(new Error('offline')));
    renderChat();
    await screen.findByText('Agent 服务未连接');
  });

  it('sends message and displays streaming text response', async () => {
    mockFetch.mockImplementation((url: string, opts?: RequestInit) => {
      if (url === '/agent-api/health') {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(HEALTH_OK) });
      }
      if (url === '/agent-api/chat/stream') {
        return sseChunks(
          { type: 'text', text: '你有 8 个视频。' },
          { type: 'text', text: '包括教程、游戏等。', finish: true },
          null,
        );
      }
      return Promise.reject(new Error('unexpected'));
    });

    renderChat();
    await screen.findByText(/MiniMax/);

    const input = screen.getByPlaceholderText(/问任何关于你视频的问题/);
    await userEvent.type(input, '我有几个视频？');
    await userEvent.click(screen.getByTitle('发送'));

    await screen.findByText('我有几个视频？');
    await screen.findByText(/你有 8 个视频/);
    await screen.findByText(/包括教程、游戏等/);
  });

  it('displays tool call cards when SSE includes tool_call events', async () => {
    mockFetch.mockImplementation((url: string, opts?: RequestInit) => {
      if (url === '/agent-api/health') {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(HEALTH_OK) });
      }
      if (url === '/agent-api/chat/stream') {
        return sseChunks(
          {
            type: 'tool_call',
            tool: { name: 'list_my_videos', args: { limit: 20 }, result: '{"list": [{"id":"v1","title":"测试视频"}]}' },
          },
          { type: 'text', text: '你有 1 个视频。', finish: true },
          null,
        );
      }
      return Promise.reject(new Error('unexpected'));
    });

    renderChat();
    await screen.findByText(/MiniMax/);

    const input = screen.getByPlaceholderText(/问任何关于你视频的问题/);
    await userEvent.type(input, '我有几个视频？');
    await userEvent.click(screen.getByTitle('发送'));

    await screen.findByText('list_my_videos');
    await screen.findByText('你有 1 个视频。');
  });

  it('fills input when quick action button clicked', async () => {
    renderChat();
    await screen.findByText('我上传了几个视频？');

    await userEvent.click(screen.getByText('我上传了几个视频？'));
    const input = screen.getByPlaceholderText(/问任何关于你视频的问题/) as HTMLTextAreaElement;
    expect(input.value).toBe('我上传了几个视频？');
  });

  it('shows stop button while loading', async () => {
    mockFetch.mockImplementation((url: string, opts?: RequestInit) => {
      if (url === '/agent-api/health') {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(HEALTH_OK) });
      }
      if (url === '/agent-api/chat/stream') {
        return new Promise(() => {}); // hangs forever
      }
      return Promise.reject(new Error('unexpected'));
    });

    renderChat();
    await screen.findByText(/MiniMax/);

    const input = screen.getByPlaceholderText(/问任何关于你视频的问题/);
    await userEvent.type(input, '测试');
    await userEvent.click(screen.getByTitle('发送'));

    await screen.findByText('■');
  });
});
