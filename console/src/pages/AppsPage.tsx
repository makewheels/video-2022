import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type OAuthApp } from '../api/client';
import AppCard from '../components/AppCard';

const AVAILABLE_SCOPES = ['video:read', 'video:write', 'user:read', 'comment:read', 'comment:write'];

export default function AppsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [redirectUris, setRedirectUris] = useState('');
  const [scopes, setScopes] = useState<string[]>([]);
  const [newApp, setNewApp] = useState<OAuthApp | null>(null);

  const { data: apps, isLoading } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });

  const createMutation = useMutation({
    mutationFn: (data: { name: string; description: string; redirectUris: string[]; scopes: string[] }) =>
      apiClient.createApp(data),
    onSuccess: (app) => {
      setNewApp(app);
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['apps'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteApp(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['apps'] });
    },
  });

  const resetForm = () => {
    setName('');
    setDescription('');
    setRedirectUris('');
    setScopes([]);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const uris = redirectUris
      .split('\n')
      .map((u) => u.trim())
      .filter(Boolean);
    createMutation.mutate({ name, description, redirectUris: uris, scopes });
  };

  const toggleScope = (scope: string) => {
    setScopes((prev) => (prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope]));
  };

  const handleDelete = (id: string) => {
    if (confirm('确定要删除这个应用吗？此操作不可撤销。')) {
      deleteMutation.mutate(id);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>我的应用</h1>
        <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '取消' : '+ 创建应用'}
        </button>
      </div>

      {newApp && (
        <div className="alert alert-success">
          <strong>应用创建成功！</strong> 请保存下方的 Client Secret，它只会显示一次。
          <AppCard app={newApp} onDelete={handleDelete} webhookHref={`/webhooks?appId=${newApp.id}`} />
          <button className="btn btn-sm" onClick={() => setNewApp(null)}>关闭</button>
        </div>
      )}

      {showForm && (
        <div className="create-app-form">
          <h3>创建新应用</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="app-name">应用名称</label>
              <input
                id="app-name"
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                placeholder="我的应用"
              />
            </div>
            <div className="form-group">
              <label htmlFor="app-desc">应用描述</label>
              <textarea
                id="app-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="描述你的应用用途"
                rows={3}
              />
            </div>
            <div className="form-group">
              <label htmlFor="app-uris">回调地址 (每行一个)</label>
              <textarea
                id="app-uris"
                value={redirectUris}
                onChange={(e) => setRedirectUris(e.target.value)}
                placeholder="https://example.com/callback"
                rows={3}
              />
            </div>
            <div className="form-group">
              <label>权限范围</label>
              <div className="scope-checkboxes">
                {AVAILABLE_SCOPES.map((scope) => (
                  <label key={scope} className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={scopes.includes(scope)}
                      onChange={() => toggleScope(scope)}
                    />
                    {scope}
                  </label>
                ))}
              </div>
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
          <AppCard key={app.id} app={app} onDelete={handleDelete} webhookHref={`/webhooks?appId=${app.id}`} />
        ))}
        {apps && apps.length === 0 && !showForm && (
          <div className="empty-state">
            <p>还没有创建应用</p>
            <button className="btn btn-primary" onClick={() => setShowForm(true)}>创建第一个应用</button>
          </div>
        )}
      </div>
    </div>
  );
}
