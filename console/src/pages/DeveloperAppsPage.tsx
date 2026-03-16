import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { apiClient, type DeveloperAppItem } from '../api/client';

export default function DeveloperAppsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [showForm, setShowForm] = useState(false);
  const [appName, setAppName] = useState('');
  const [newApp, setNewApp] = useState<DeveloperAppItem | null>(null);

  const { data: apps, isLoading } = useQuery({
    queryKey: ['developer-apps'],
    queryFn: () => apiClient.listDeveloperApps(),
  });

  const createMutation = useMutation({
    mutationFn: (data: { appName: string }) => apiClient.createDeveloperApp(data),
    onSuccess: (app) => {
      setNewApp(app);
      setShowForm(false);
      setAppName('');
      queryClient.invalidateQueries({ queryKey: ['developer-apps'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (appId: string) => apiClient.deleteDeveloperApp(appId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['developer-apps'] });
    },
  });

  const regenerateMutation = useMutation({
    mutationFn: (appId: string) => apiClient.regenerateDeveloperAppSecret(appId),
    onSuccess: (app) => {
      setNewApp(app);
      queryClient.invalidateQueries({ queryKey: ['developer-apps'] });
    },
  });

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).catch(() => {
      const el = document.createElement('textarea');
      el.value = text;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
    });
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMutation.mutate({ appName });
  };

  const handleDelete = (appId: string) => {
    if (confirm('确定要删除这个应用吗？此操作不可撤销。')) {
      deleteMutation.mutate(appId);
    }
  };

  const handleRegenerate = (appId: string) => {
    if (confirm('确定要重新生成 Secret 吗？旧的 Secret 将立即失效。')) {
      regenerateMutation.mutate(appId);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>开发者应用</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '取消' : '+ 创建应用'}
        </button>
      </div>

      {newApp && (
        <div className="alert alert-success">
          <strong>操作成功！</strong> 请保存下方的 App Secret，它仅在此时显示。
          <div className="app-card" style={{ marginTop: 12 }}>
            <div className="app-field">
              <label>App ID</label>
              <div className="copyable">
                <code>{newApp.appId}</code>
                <button className="btn-copy" onClick={() => copyToClipboard(newApp.appId)} title="复制">📋</button>
              </div>
            </div>
            {newApp.appSecret && (
              <div className="app-field">
                <label>App Secret</label>
                <div className="copyable">
                  <code className="secret">{newApp.appSecret}</code>
                  <button className="btn-copy" onClick={() => copyToClipboard(newApp.appSecret!)} title="复制">📋</button>
                </div>
                <p className="warning">⚠️ 请妥善保存 Secret，此后将不再显示</p>
              </div>
            )}
          </div>
          <button className="btn btn-sm" onClick={() => setNewApp(null)}>关闭</button>
        </div>
      )}

      {showForm && (
        <div className="create-app-form">
          <h3>创建新应用</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="dev-app-name">应用名称</label>
              <input
                id="dev-app-name"
                type="text"
                value={appName}
                onChange={(e) => setAppName(e.target.value)}
                required
                placeholder="我的开发者应用"
              />
            </div>
            <button type="submit" className="btn btn-primary" disabled={createMutation.isPending}>
              {createMutation.isPending ? '创建中...' : '创建应用'}
            </button>
            {createMutation.isError && (
              <div className="alert alert-error">
                {createMutation.error instanceof Error ? createMutation.error.message : '创建失败'}
              </div>
            )}
          </form>
        </div>
      )}

      {isLoading && <p>加载中...</p>}

      <div className="app-list">
        {apps?.map((app) => (
          <div key={app.id} className="app-card">
            <div className="app-card-header">
              <h3>{app.appName}</h3>
              <div>
                <button
                  className="btn btn-sm"
                  onClick={() => navigate(`/developer-apps/${app.appId}/webhooks`)}
                  style={{ marginRight: 8 }}
                >
                  🔔 Webhook 配置
                </button>
                <button
                  className="btn btn-sm"
                  onClick={() => handleRegenerate(app.appId)}
                  disabled={regenerateMutation.isPending}
                  style={{ marginRight: 8 }}
                >
                  🔄 重新生成 Secret
                </button>
                <button className="btn-danger btn-sm" onClick={() => handleDelete(app.appId)}>
                  删除
                </button>
              </div>
            </div>
            <div className="app-field">
              <label>App ID</label>
              <div className="copyable">
                <code>{app.appId}</code>
                <button className="btn-copy" onClick={() => copyToClipboard(app.appId)} title="复制">📋</button>
              </div>
            </div>
            <div className="app-field">
              <label>状态</label>
              <span className={`scope-badge ${app.status === 'active' ? 'webhook-active' : ''}`}>
                {app.status}
              </span>
            </div>
            {app.webhookUrl && (
              <div className="app-field">
                <label>Webhook URL</label>
                <code>{app.webhookUrl}</code>
              </div>
            )}
            {app.webhookEvents && app.webhookEvents.length > 0 && (
              <div className="app-field">
                <label>订阅事件</label>
                <div className="scopes">
                  {app.webhookEvents.map((ev) => (
                    <span key={ev} className="scope-badge">{ev}</span>
                  ))}
                </div>
              </div>
            )}
            <div className="app-meta">
              创建时间: {new Date(app.createdAt).toLocaleDateString('zh-CN')}
            </div>
          </div>
        ))}
        {apps && apps.length === 0 && !showForm && (
          <div className="empty-state">
            <p>还没有创建开发者应用</p>
            <button className="btn btn-primary" onClick={() => setShowForm(true)}>创建第一个应用</button>
          </div>
        )}
      </div>
    </div>
  );
}
