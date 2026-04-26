import { useState, useRef, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useToast } from '../utils/toast';
import { isLoggedIn } from '../utils/auth';
import './ChatPage.css';

interface ToolCall {
  name: string;
  args: Record<string, unknown>;
  result?: unknown;
  pending: boolean;
}

interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  done: boolean;
  tools?: ToolCall[];
  confirmNeeded?: boolean;
}

const QUICK_ACTIONS = [
  '我上传了几个视频？',
  'AI 教程播放量是多少？',
  '搜索美食类公开视频',
  '我最早上传的视频是什么？',
  '我有几条未读通知？',
  '哪个视频播放量最高？',
];

let nextId = 1;

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [serverStatus, setServerStatus] = useState<'checking' | 'online' | 'offline'>('checking');
  const [serverInfo, setServerInfo] = useState<Record<string, unknown> | null>(null);
  const [sessionId] = useState(() => crypto.randomUUID());
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const toast = useToast();

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

      const reader = resp.body!.getReader();
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
            const event = JSON.parse(payload);
            setMessages(prev => {
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
            });
          } catch { /* skip malformed */ }
        }
      }
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
  }, [input, loading, toast]);

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

  return (
    <div className="chat-page">
      {/* header */}
      <div className="chat-header">
        <span className="chat-title">🤖 AI 视频助手</span>
        <span className={`chat-status ${serverStatus}`}>
          <span className="status-dot" />
          {serverStatus === 'checking' ? '检查中…' : serverStatus === 'online'
            ? `${serverInfo?.model || ''} · ${(serverInfo?.backend as string) === 'fixture' ? '测试模式' : '在线'}`
            : 'Agent 服务未连接'}
        </span>
      </div>

      {/* messages */}
      <div className="chat-messages">
        {messages.length === 0 && (
          <div className="chat-welcome">
            <h2>👋 欢迎使用 AI 视频助手</h2>
            <p>用自然语言管理你的视频平台：查视频、看数据、搜内容、管播放列表...</p>
            <div className="chat-quick-actions">
              {QUICK_ACTIONS.map(q => (
                <button key={q} onClick={() => { setInput(q); inputRef.current?.focus(); }}>
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map(msg => (
          <div key={msg.id} className={`chat-msg ${msg.role}`}>
            <div className="chat-msg-avatar">{msg.role === 'user' ? '👤' : '🤖'}</div>
            <div className="chat-msg-body">
              <div className={`chat-msg-content${msg.role === 'assistant' ? ' chat-msg-md' : ''}`}>
                {msg.role === 'assistant' ? (
                  msg.content ? (
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                  ) : msg.done ? (
                    '...'
                  ) : null
                ) : (
                  msg.content || (msg.done ? '...' : '')
                )}
                {!msg.done && !msg.content && <span className="chat-loading"><span/><span/><span/></span>}
              </div>

              {/* Tool calls */}
              {msg.tools && msg.tools.map((tc, i) => (
                <details key={i} className={`chat-tool-card${tc.pending ? ' chat-tool-pending' : ''}`} open>
                  <summary>
                    {tc.pending ? (
                      <span className="chat-tool-spinner" />
                    ) : (
                      '🔧'
                    )}{' '}
                    <code>{tc.name}</code>
                    {tc.pending ? ' 执行中…' : ''}
                  </summary>
                  {!tc.pending && (
                    <div className="chat-tool-details">
                      <div className="chat-tool-section">
                        <div className="chat-tool-label">参数</div>
                        <pre className="chat-tool-json">{JSON.stringify(tc.args, null, 2)}</pre>
                      </div>
                      <div className="chat-tool-section">
                        <div className="chat-tool-label">结果</div>
                        <pre className="chat-tool-json">{(() => {
                          if (!tc.result) return '{}';
                          if (typeof tc.result === 'string') {
                            try {
                              const parsed = JSON.parse(tc.result);
                              return JSON.stringify(parsed, null, 2);
                            } catch {
                              return tc.result;
                            }
                          }
                          return JSON.stringify(tc.result, null, 2);
                        })()}</pre>
                      </div>
                    </div>
                  )}
                </details>
              ))}

              {/* Confirmation */}
              {msg.confirmNeeded && (
                <div className="chat-confirm-banner">
                  ⚠️ 以上写操作需要确认。在命令行中使用 <code>--confirm-write</code> 或在服务中开启写确认模式。
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={chatEndRef} />
      </div>

      {/* input */}
      <div className="chat-input-area">
        <div className="chat-input-row">
          <textarea
            ref={inputRef}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={isLoggedIn() ? '问任何关于你视频的问题… (Enter 发送)' : '请先登录后使用 AI 助手'}
            rows={1}
            disabled={loading || !isLoggedIn()}
          />
          {loading ? (
            <button className="chat-send-btn stop" onClick={stop} title="停止">■</button>
          ) : (
            <button className="chat-send-btn" onClick={send} disabled={!input.trim() || !isLoggedIn()} title="发送">
              ▶
            </button>
          )}
        </div>
        {!isLoggedIn() && (
          <p className="chat-login-hint">
            请先 <Link to="/login">登录</Link> 后使用 AI 助手
          </p>
        )}
      </div>
    </div>
  );
}
