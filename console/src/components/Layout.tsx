import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function Layout() {
  const { developer, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-header">
          <h2>🎬 开发者平台</h2>
        </div>
        <nav className="sidebar-nav">
          <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            📊 控制台
          </NavLink>
          <NavLink to="/apps" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            📱 我的应用
          </NavLink>
          <NavLink to="/docs" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            📖 API 文档
          </NavLink>
          <NavLink to="/stats" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            📈 用量统计
          </NavLink>
          <NavLink to="/webhooks" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            🔔 Webhook
          </NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="dev-info">
            <span className="dev-name">{developer?.name}</span>
            <span className="dev-email">{developer?.email}</span>
          </div>
          <button className="btn-logout" onClick={handleLogout}>退出登录</button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
