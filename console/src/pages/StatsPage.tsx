import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { Link } from 'react-router-dom';

export default function StatsPage() {
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['developer-stats'],
    queryFn: () => apiClient.getStats(),
  });
  const { data: apps, isLoading } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });

  const appCount = stats?.appCount ?? apps?.length ?? 0;
  const totalApiRequests = stats?.totalApiRequests ?? 0;
  const webhookDeliveries = stats?.webhookDeliveryCount ?? 0;

  return (
    <div className="page">
      <h1>用量统计</h1>

      {(isLoading || statsLoading) && <p>加载中...</p>}

      {!isLoading && !statsLoading && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-number">{appCount}</div>
              <div className="stat-label">应用总数</div>
            </div>
            <div className="stat-card">
              <div className="stat-number">{totalApiRequests}</div>
              <div className="stat-label">累计 API 调用</div>
            </div>
            <div className="stat-card">
              <div className="stat-number">{webhookDeliveries}</div>
              <div className="stat-label">累计 Webhook 投递</div>
            </div>
          </div>

          <div className="alert alert-success" style={{ marginBottom: 20 }}>
            当前统计基于开发者应用、限流记录和 Webhook 投递记录实时汇总。
          </div>

          <h2 style={{ fontSize: 18, marginBottom: 12 }}>应用列表</h2>

          {appCount === 0 ? (
            <div className="empty-state">
              <p>还没有创建应用</p>
              <Link to="/apps" className="btn btn-primary">去创建应用</Link>
            </div>
          ) : (
            <div className="app-list">
              {apps!.map((app) => (
                <div key={app.id} className="app-card">
                  <div className="app-card-header">
                    <h3>{app.name}</h3>
                  </div>
                  {app.description && (
                    <div className="app-desc">{app.description}</div>
                  )}
                  <div className="app-field">
                    <label>Client ID</label>
                    <div className="copyable">
                      <code>{app.clientId}</code>
                    </div>
                  </div>
                  <div className="app-field">
                    <label>权限范围</label>
                    <div className="scopes">
                      {app.scopes.map((s) => (
                        <span key={s} className="scope-badge">{s}</span>
                      ))}
                    </div>
                  </div>
                  <div className="app-meta">
                    创建于 {app.createTime ? new Date(app.createTime).toLocaleDateString('zh-CN') : '—'}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
