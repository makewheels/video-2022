import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type WebhookConfig } from '../api/client';
import { useSearchParams } from 'react-router-dom';

const AVAILABLE_EVENTS = [
  'video.upload.completed',
  'video.transcode.completed',
  'video.transcode.failed',
  'video.deleted',
];

export default function WebhooksPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedAppId, setSelectedAppId] = useState(searchParams.get('appId') || '');
  const [showForm, setShowForm] = useState(false);
  const [url, setUrl] = useState('');
  const [events, setEvents] = useState<string[]>([]);

  const { data: apps, isLoading: appsLoading } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });

  const {
    data: webhooks,
    isLoading: webhooksLoading,
    error: webhooksError,
  } = useQuery({
    queryKey: ['webhooks', selectedAppId],
    queryFn: () => apiClient.listWebhooks(selectedAppId),
    enabled: !!selectedAppId,
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: (data: { url: string; events: string[] }) =>
      apiClient.createWebhook(selectedAppId, data),
    onSuccess: () => {
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['webhooks', selectedAppId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (webhookId: string) =>
      apiClient.deleteWebhook(selectedAppId, webhookId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', selectedAppId] });
    },
  });

  const resetForm = () => {
    setUrl('');
    setEvents([]);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMutation.mutate({ url, events });
  };

  const toggleEvent = (event: string) => {
    setEvents((prev) =>
      prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event]
    );
  };

  const handleDelete = (webhook: WebhookConfig) => {
    if (confirm(`确定要删除 Webhook (${webhook.url}) 吗？`)) {
      deleteMutation.mutate(webhook.id);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Webhook 管理</h1>
        {selectedAppId && (
          <button
            className="btn btn-primary"
            onClick={() => setShowForm(!showForm)}
          >
            {showForm ? '取消' : '+ 添加 Webhook'}
          </button>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="app-select">选择应用</label>
        <select
          id="app-select"
          className="webhook-select"
          value={selectedAppId}
          onChange={(e) => {
            const appId = e.target.value;
            setSelectedAppId(appId);
            if (appId) {
              setSearchParams({ appId });
            } else {
              setSearchParams({});
            }
            setShowForm(false);
          }}
          disabled={appsLoading}
        >
          <option value="">
            {appsLoading ? '加载中...' : '-- 请选择应用 --'}
          </option>
          {apps?.map((app) => (
            <option key={app.id} value={app.id}>
              {app.name} ({app.clientId})
            </option>
          ))}
        </select>
      </div>

      {!selectedAppId && !appsLoading && apps && apps.length === 0 && (
        <div className="empty-state">
          <p>还没有创建应用，请先创建一个应用。</p>
        </div>
      )}

      {selectedAppId && showForm && (
        <div className="create-app-form">
          <h3>添加 Webhook</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="webhook-url">回调地址</label>
              <input
                id="webhook-url"
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                required
                placeholder="https://example.com/webhook"
              />
            </div>
            <div className="form-group">
              <label>事件类型</label>
              <div className="scope-checkboxes">
                {AVAILABLE_EVENTS.map((event) => (
                  <label key={event} className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={events.includes(event)}
                      onChange={() => toggleEvent(event)}
                    />
                    {event}
                  </label>
                ))}
              </div>
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={createMutation.isPending || events.length === 0}
            >
              {createMutation.isPending ? '创建中...' : '创建 Webhook'}
            </button>
            {createMutation.isError && (
              <div className="alert alert-error" style={{ marginTop: 12 }}>
                {createMutation.error instanceof Error
                  ? createMutation.error.message
                  : '创建失败'}
              </div>
            )}
          </form>
        </div>
      )}

      {selectedAppId && webhooksLoading && <p>加载中...</p>}
      {selectedAppId && webhooksError instanceof Error && (
        <div className="alert alert-error">{webhooksError.message}</div>
      )}

      {selectedAppId && !webhooksError && webhooks && (
        <div className="app-list">
          {webhooks.map((wh) => (
            <div key={wh.id} className="app-card">
              <div className="app-card-header">
                <h3>{wh.url}</h3>
                <button
                  className="btn btn-danger btn-sm"
                  onClick={() => handleDelete(wh)}
                  disabled={deleteMutation.isPending}
                >
                  删除
                </button>
              </div>
              <div className="app-field">
                <label>事件类型</label>
                <div className="scopes">
                  {wh.events.map((ev) => (
                    <span key={ev} className="scope-badge">{ev}</span>
                  ))}
                </div>
              </div>
              <div className="app-field">
                <label>状态</label>
                <span
                  className={`scope-badge ${wh.status === 'active' ? 'webhook-active' : ''}`}
                >
                  {wh.status}
                </span>
              </div>
              <div className="app-meta">
                创建于 {new Date(wh.createTime).toLocaleDateString('zh-CN')}
              </div>
            </div>
          ))}
          {webhooks.length === 0 && !showForm && (
            <div className="empty-state">
              <p>该应用还没有配置 Webhook</p>
              <button
                className="btn btn-primary"
                onClick={() => setShowForm(true)}
              >
                添加第一个 Webhook
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
