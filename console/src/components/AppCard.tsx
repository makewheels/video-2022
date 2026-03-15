import type { OAuthApp } from '../api/client';

interface AppCardProps {
  app: OAuthApp;
  onDelete: (id: string) => void;
}

export default function AppCard({ app, onDelete }: AppCardProps) {
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).catch(() => {
      // Fallback: select text for manual copy
      const el = document.createElement('textarea');
      el.value = text;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
    });
  };

  return (
    <div className="app-card">
      <div className="app-card-header">
        <h3>{app.name}</h3>
        <button className="btn-danger btn-sm" onClick={() => onDelete(app.id)}>删除</button>
      </div>
      {app.description && <p className="app-desc">{app.description}</p>}
      <div className="app-field">
        <label>Client ID</label>
        <div className="copyable">
          <code>{app.clientId}</code>
          <button className="btn-copy" onClick={() => copyToClipboard(app.clientId)} title="复制">📋</button>
        </div>
      </div>
      {app.clientSecret && (
        <div className="app-field">
          <label>Client Secret</label>
          <div className="copyable">
            <code className="secret">{app.clientSecret}</code>
            <button className="btn-copy" onClick={() => copyToClipboard(app.clientSecret!)} title="复制">📋</button>
          </div>
          <p className="warning">⚠️ 请妥善保存 Secret，此后将不再显示</p>
        </div>
      )}
      {app.scopes && app.scopes.length > 0 && (
        <div className="app-field">
          <label>权限范围</label>
          <div className="scopes">
            {app.scopes.map((s) => (
              <span key={s} className="scope-badge">{s}</span>
            ))}
          </div>
        </div>
      )}
      <div className="app-meta">
        创建时间: {new Date(app.createdAt).toLocaleDateString('zh-CN')}
      </div>
    </div>
  );
}
