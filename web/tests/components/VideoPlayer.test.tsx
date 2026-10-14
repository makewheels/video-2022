import { render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const playerHandlers: Record<string, Array<() => void>> = {};
const mockPlayer = {
  src: vi.fn(),
  on: vi.fn((event: string, handler: () => void) => {
    playerHandlers[event] ??= [];
    playerHandlers[event].push(handler);
  }),
  off: vi.fn((event: string, handler: () => void) => {
    playerHandlers[event] = (playerHandlers[event] || []).filter((item) => item !== handler);
  }),
  currentTime: vi.fn(() => 12.345),
  paused: vi.fn(() => false),
  volume: vi.fn(() => 1),
  muted: vi.fn(),
  isFullscreen: vi.fn(() => false),
  exitFullscreen: vi.fn(),
  requestFullscreen: vi.fn(),
  isDisposed: vi.fn(() => false),
  dispose: vi.fn(),
  videoHeight: vi.fn(() => 720),
  play: vi.fn(),
  pause: vi.fn(),
};

vi.mock('video.js', () => ({
  default: vi.fn(() => mockPlayer),
}));

vi.mock('../../src/utils/api', () => ({
  default: { post: vi.fn() },
}));

import api from '../../src/utils/api';
import VideoPlayer from '../../src/components/VideoPlayer';

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

describe('VideoPlayer', () => {
  const originalVisibilityState = document.visibilityState;
  const originalSendBeacon = navigator.sendBeacon;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    Object.keys(playerHandlers).forEach((key) => delete playerHandlers[key]);
    vi.mocked(api.post).mockImplementation((url: string) => {
      if (url === '/playback/start') {
        return Promise.resolve({ data: { data: { playbackSessionId: 'ps_001' } } });
      }
      return Promise.resolve({ data: { data: {} } });
    });
    Object.defineProperty(navigator, 'sendBeacon', {
      configurable: true,
      value: vi.fn(() => true),
    });
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: originalVisibilityState,
    });
    Object.defineProperty(navigator, 'sendBeacon', {
      configurable: true,
      value: originalSendBeacon,
    });
  });

  it('sends playback heartbeats with playbackSessionId and exits on unmount', async () => {
    const { unmount } = render(
      <VideoPlayer src="https://example.com/video.m3u8" videoId="v_1" watchId="w_1" />,
    );

    await flushPromises();
    await vi.advanceTimersByTimeAsync(15000);
    await flushPromises();

    expect(api.post).toHaveBeenCalledWith('/playback/heartbeat', expect.objectContaining({
      playbackSessionId: 'ps_001',
      currentTimeMs: 12345,
      isPlaying: true,
      resolution: '720p',
    }));

    unmount();
    await flushPromises();

    expect(api.post).toHaveBeenCalledWith('/playback/exit', expect.objectContaining({
      playbackSessionId: 'ps_001',
      currentTimeMs: 12345,
      exitType: 'NAVIGATE_AWAY',
      resolution: '720p',
    }));
  });

  it('uses sendBeacon when the page becomes hidden', async () => {
    render(
      <VideoPlayer src="https://example.com/video.m3u8" videoId="v_2" watchId="w_2" />,
    );

    await flushPromises();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });
    document.dispatchEvent(new Event('visibilitychange'));

    expect(navigator.sendBeacon).toHaveBeenCalledWith(
      '/playback/exit',
      expect.any(Blob),
    );
  });
  it.each([
    ['ArrowLeft', 7.345], ['ArrowRight', 17.345], ['j', 2.345], ['l', 22.345],
  ])('preserves %s seek offsets and prevents default', (key, expected) => {
    const { unmount } = render(
      <VideoPlayer src="https://example.com/video.m3u8" videoId="v_key" watchId="w_key" />,
    );
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    document.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(mockPlayer.currentTime).toHaveBeenLastCalledWith(expect.closeTo(expected, 3));
    unmount();
  });

  it('leaves input typing and unknown keys unchanged', () => {
    const { unmount } = render(
      <VideoPlayer src="https://example.com/video.m3u8" videoId="v_key" watchId="w_key" />,
    );
    const input = document.createElement('input');
    document.body.append(input);
    const typing = new KeyboardEvent('keydown', { key: 'k', bubbles: true, cancelable: true });
    input.dispatchEvent(typing);
    const unknown = new KeyboardEvent('keydown', { key: 'toString', cancelable: true });
    document.dispatchEvent(unknown);
    expect(typing.defaultPrevented).toBe(false);
    expect(unknown.defaultPrevented).toBe(false);
    expect(mockPlayer.play).not.toHaveBeenCalled();
    expect(mockPlayer.pause).not.toHaveBeenCalled();
    input.remove();
    unmount();
  });

});
