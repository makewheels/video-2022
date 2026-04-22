import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';

export default function DashboardPage() {
  const { developer } = useAuth();

  const { data: apps } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });
  const { data: stats } = useQuery({
    queryKey: ['developer-stats'],
    queryFn: () => apiClient.getStats(),
  });

  return (
    <div className="page">
      <h1>控制台</h1>
      <div className="welcome-banner">
        <h2>欢迎回来，{developer?.name} 👋</h2>
        <p>{developer?.email}{developer?.company ? ` · ${developer.company}` : ''}</p>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-number">{stats?.appCount ?? apps?.length ?? '—'}</div>
          <div className="stat-label">已创建应用</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats?.totalApiRequests ?? '—'}</div>
          <div className="stat-label">累计 API 调用</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats?.webhookDeliveryCount ?? '—'}</div>
          <div className="stat-label">累计 Webhook 投递</div>
        </div>
      </div>

      <div className="quick-links">
        <h3>快速入口</h3>
        <div className="link-grid">
          <Link to="/apps" className="link-card">
            <span className="link-icon">📱</span>
            <span>管理应用</span>
          </Link>
          <Link to="/docs" className="link-card">
            <span className="link-icon">📖</span>
            <span>API 文档</span>
          </Link>
          <Link to="/stats" className="link-card">
            <span className="link-icon">📈</span>
            <span>用量统计</span>
          </Link>
          <Link to="/webhooks" className="link-card">
            <span className="link-icon">🔔</span>
            <span>Webhook</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
