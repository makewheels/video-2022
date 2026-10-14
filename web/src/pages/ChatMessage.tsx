import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Message } from './chatStream';

export default function ChatMessage({ msg }: { msg: Message }) {
  return (
    <div className={`chat-msg ${msg.role}`}>
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
  );
}
