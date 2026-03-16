import { useState, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';

const WEBHOOK_EVENTS = [
  'video.created',
  'video.transcoded',
  'video.deleted',
  'comment.created',
];

export default function DeveloperWebhooksPage() {
  const { appId } = useParams<{ appId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [webhookUrl, setWebhookUrl] = useState('');
  const [selectedEvents, setSelectedEvents] = useState<string[]>([]);
  const [editing, setEditing] = useState(false);

  const { data: apps } = useQuery({
    queryKey: ['developer-apps'],
    queryFn: () => apiClient.listDeveloperApps(),
  });

  const app = apps?.find((a) => a.appId === appId);

  const updateMutation = useMutation({
    mutationFn: (data: { webhookUrl: string; webhookEvents: string[] }) =>
      apiClient.updateDeveloperApp(appId!, data),
    onSuccess: () => {
      setEditing(false);
      queryClient.invalidateQueries({ queryKey: ['developer-apps'] });
    },
  });

  const testMutation = useMutation({
    mutationFn: () => apiClient.testDeveloperWebhook(appId!),
  });

  const toggleEvent = (event: string) => {
    setSelectedEvents((prev) =>
      prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event]
    );
  };

  const handleEdit = () => {
    if (app) {
      setWebhookUrl(app.webhookUrl || '');
      setSelectedEvents(app.webhookEvents || []);
      setEditing(true);
    }
  };

  const handleSave = (e: FormEvent) => {
    e.preventDefault();
    updateMutation.mutate({ webhookUrl, webhookEvents: selectedEvents });
  };

  const handleTest = () => {
    testMutation.mutate();
  };

  if (!appId) {
    return <div className="page"><p>无效的应用 ID</p></div>;
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>
          <button className="btn btn-sm" onClick={() => navigate('/developer-apps')} style={{ marginRight: 12 }}>
            ← 返回
          </button>
          Webhook 配置
        </h1>
      </div>

      {app && (
        <div className="app-card" style={{ marginBottom: 24 }}>
          <h3>{app.appName}</h3>
          <div className="app-field">
            <label>App ID</label>
            <code>{app.appId}</code>
          </div>
        </div>
      )}

      {!editing && app && (
        <div className="app-card">
          <div className="app-card-header">
            <h3>当前 Webhook 配置</h3>
            <div>
              <button className="btn btn-sm" onClick={handleEdit} style={{ marginRight: 8 }}>
                ✏️ 编辑
              </button>
              <button
                className="btn btn-sm"
                onClick={handleTest}
                disabled={testMutation.isPending || !app.webhookUrl}
              >
                {testMutation.isPending ? '发送中...' : '🧪 发送测试事件'}
              </button>
            </div>
          </div>
          <div className="app-field">
            <label>Webhook URL</label>
            <code>{app.webhookUrl || '未配置'}</code>
          </div>
          <div className="app-field">
            <label>订阅事件</label>
            {app.webhookEvents && app.webhookEvents.length > 0 ? (
              <div className="scopes">
                {app.webhookEvents.map((ev) => (
                  <span key={ev} className="scope-badge">{ev}</span>
                ))}
              </div>
            ) : (
              <span>未配置</span>
            )}
          </div>
          {testMutation.isSuccess && (
            <div className="alert alert-success" style={{ marginTop: 12 }}>
              ✅ 测试事件已发送
            </div>
          )}
          {testMutation.isError && (
            <div className="alert alert-error" style={{ marginTop: 12 }}>
              {testMutation.error instanceof Error ? testMutation.error.message : '发送失败'}
            </div>
          )}
        </div>
      )}

      {editing && (
        <div className="create-app-form">
          <h3>编辑 Webhook 配置</h3>
          <form onSubmit={handleSave}>
            <div className="form-group">
              <label htmlFor="wh-url">Webhook URL</label>
              <input
                id="wh-url"
                type="url"
                value={webhookUrl}
                onChange={(e) => setWebhookUrl(e.target.value)}
                placeholder="https://example.com/webhook"
              />
            </div>
            <div className="form-group">
              <label>订阅事件</label>
              <div className="scope-checkboxes">
                {WEBHOOK_EVENTS.map((event) => (
                  <label key={event} className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={selectedEvents.includes(event)}
                      onChange={() => toggleEvent(event)}
                    />
                    {event}
                  </label>
                ))}
              </div>
            </div>
            <div>
              <button type="submit" className="btn btn-primary" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? '保存中...' : '保存'}
              </button>
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => setEditing(false)}
                style={{ marginLeft: 8 }}
              >
                取消
              </button>
            </div>
            {updateMutation.isError && (
              <div className="alert alert-error" style={{ marginTop: 12 }}>
                {updateMutation.error instanceof Error ? updateMutation.error.message : '保存失败'}
              </div>
            )}
          </form>
        </div>
      )}
    </div>
  );
}
