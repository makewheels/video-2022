import { useState, useRef, useEffect, useCallback } from 'react';
import { useToast } from '../utils/toast';
import { isLoggedIn } from '../utils/auth';
import { readChatStream, type Message } from './chatStream';

let nextId = 1;

export function useChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [serverStatus, setServerStatus] = useState<'checking' | 'online' | 'offline'>('checking');
  const [serverInfo, setServerInfo] = useState<Record<string, unknown> | null>(null);
  const [sessionId] = useState(() => crypto.randomUUID());
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const { toast } = useToast();

  // ── Health check ──
  useEffect(() => {
    fetch('/agent-api/health')
      .then(r => r.json())
      .then(d => { setServerStatus('online'); setServerInfo(d); })
      .catch(() => setServerStatus('offline'));
  }, []);

  // ── Auto-scroll ──
  useEffect(() => { chatEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  // ── Send ──
  const send = useCallback(async () => {
    const text = input.trim();
    if (!text || loading) return;
    if (!isLoggedIn()) {
      toast('请先登录', 'error');
      return;
    }

    setInput('');
    setLoading(true);

    const userMsg: Message = { id: nextId++, role: 'user', content: text, done: true };
    const assistantMsg: Message = { id: nextId++, role: 'assistant', content: '', done: false, tools: [] };
    setMessages(prev => [...prev, userMsg, assistantMsg]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const token = localStorage.getItem('token') || '';
      const resp = await fetch('/agent-api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', token },
        body: JSON.stringify({ query: text, session_id: sessionId }),
        signal: controller.signal,
      });

      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);

      await readChatStream(resp, setMessages);
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;

      setMessages(prev => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last && last.role === 'assistant') {
          updated[updated.length - 1] = {
            ...last,
            content: last.content + '\n\n❌ 连接出错',
            done: true,
          };
        }
        return updated;
      });
      const errMsg = err instanceof Error ? err.message : 'Unknown error';
      toast(`请求失败: ${errMsg}`, 'error');
    } finally {
      setLoading(false);
      abortRef.current = null;
      inputRef.current?.focus();
    }
  }, [input, loading, toast, sessionId]);

  // ── Keyboard ──
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
  };

  // ── Stop ──
  const stop = () => {
    abortRef.current?.abort();
    setLoading(false);
    setMessages(prev => {
      const updated = [...prev];
      const last = updated[updated.length - 1];
      if (last && last.role === 'assistant') {
        updated[updated.length - 1] = { ...last, done: true };
      }
      return updated;
    });
  };

  return {
    messages,
    input,
    setInput,
    loading,
    serverStatus,
    serverInfo,
    chatEndRef,
    inputRef,
    send,
    stop,
    handleKeyDown,
  };
}
