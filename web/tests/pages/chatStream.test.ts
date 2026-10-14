import { describe, expect, it } from 'vitest';
import { readChatStream, type Message } from '../../src/pages/chatStream';

describe('chat stream compatibility', () => {
  it('keeps split UTF-8, tool results and confirmation while skipping malformed lines', async () => {
    const tool = { name: 'list_my_videos', args: { limit: 1 }, result: 'ok' };
    const lines = [
      ': keepalive',
      'data: invalid-json',
      `data: ${JSON.stringify({ type: 'text', text: '你好' })}`,
      `data: ${JSON.stringify({ type: 'tool_start', tool })}`,
      `data: ${JSON.stringify({ type: 'tool_call', tool })}`,
      `data: ${JSON.stringify({ type: 'confirmation_needed' })}`,
      `data: ${JSON.stringify({ type: 'text', text: '世界', finish: true })}`,
      'data: [DONE]',
      '',
    ].join('\n');
    const bytes = new TextEncoder().encode(lines);
    const response = new Response(new ReadableStream({
      start(controller) {
        // One byte at a time exercises line buffering and multibyte characters.
        for (const byte of bytes) controller.enqueue(new Uint8Array([byte]));
        controller.close();
      },
    }));
    let messages: Message[] = [{ id: 1, role: 'assistant', content: '', done: false, tools: [] }];
    await readChatStream(response, update => {
      messages = typeof update === 'function' ? update(messages) : update;
    });
    expect(messages[0]).toEqual({
      id: 1, role: 'assistant', content: '你好世界', done: true,
      tools: [{ ...tool, pending: false }], confirmNeeded: true,
    });
  });
});
