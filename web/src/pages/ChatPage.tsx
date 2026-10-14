import { Link } from 'react-router-dom';
import { isLoggedIn } from '../utils/auth';
import { useChatPage } from './useChatPage';
import ChatMessage from './ChatMessage';
import './ChatPage.css';

const QUICK_ACTIONS = [
  '我上传了几个视频？',
  'AI 教程播放量是多少？',
  '搜索美食类公开视频',
  '我最早上传的视频是什么？',
  '我有几条未读通知？',
  '哪个视频播放量最高？',
];

export default function ChatPage() {
  const {
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
  } = useChatPage();

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
          <ChatMessage key={msg.id} msg={msg} />
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
