import type { Dispatch, SetStateAction } from 'react';

export interface ToolCall {
  name: string;
  args: Record<string, unknown>;
  result?: unknown;
  pending: boolean;
}

export interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  done: boolean;
  tools?: ToolCall[];
  confirmNeeded?: boolean;
}

interface StreamEvent {
  type: string;
  text?: string;
  finish?: boolean;
  tool: ToolCall;
}

export function applyStreamEvent(prev: Message[], event: StreamEvent): Message[] {
  const last = prev[prev.length - 1];
  if (!last || last.role !== 'assistant') return prev;

  if (event.type === 'text') {
    const updated = [...prev];
    updated[updated.length - 1] = {
      ...last,
      content: last.content + (event.text || ''),
      done: event.finish ?? false,
    };
    return updated;
  }

  if (event.type === 'tool_start') {
    const updated = [...prev];
    updated[updated.length - 1] = {
      ...last,
      tools: [...(last.tools || []), { ...event.tool, pending: true }],
    };
    return updated;
  }

  if (event.type === 'tool_call') {
    const updated = [...prev];
    const tools = [...(last.tools || [])];
    // Replace pending tool with result
    const idx = tools.findIndex(
      t => t.name === event.tool.name && t.pending
    );
    if (idx >= 0) {
      tools[idx] = { ...event.tool, pending: false };
    } else {
      tools.push({ ...event.tool, pending: false });
    }
    updated[updated.length - 1] = { ...last, tools };
    return updated;
  }

  if (event.type === 'confirmation_needed') {
    const updated = [...prev];
    updated[updated.length - 1] = { ...last, confirmNeeded: true };
    return updated;
  }

  return prev;
}

export async function readChatStream(response: Response, setMessages: Dispatch<SetStateAction<Message[]>>) {
  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (!line.startsWith('data: ')) continue;
      const payload = line.slice(6);
      if (payload === '[DONE]') continue;
      try {
        const event: StreamEvent = JSON.parse(payload);
        setMessages(prev => applyStreamEvent(prev, event));
      } catch { /* skip malformed */ }
    }
  }
}
