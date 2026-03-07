import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { isLoggedIn, removeToken } from '../utils/auth';
import { toggleTheme, getEffectiveTheme } from '../utils/theme';

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/upload', label: '上传' },
  { to: '/statistics', label: '统计' },
  { to: '/youtube', label: 'YouTube' },
];

export default function NavBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [theme, setThemeState] = useState(getEffectiveTheme);
  const [menuOpen, setMenuOpen] = useState(false);

  const handleToggleTheme = () => {
    const next = toggleTheme();
    setThemeState(next);
  };

  const handleLogout = () => {
    removeToken();
    navigate('/login');
  };

  return (
    <header className="page-header">
      <Link to="/" className="logo">📹 Video</Link>
      <nav className={`nav-menu${menuOpen ? ' open' : ''}`}>
        {navLinks.map(({ to, label }) => (
          <Link
            key={to}
            to={to}
            className={`nav-link${location.pathname === to ? ' active' : ''}`}
            onClick={() => setMenuOpen(false)}
          >
            {label}
          </Link>
        ))}
      </nav>
      <div className="header-right">
        <button className="btn-icon" type="button" title="切换主题" onClick={handleToggleTheme}>
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
        <div className="header-auth">
          {isLoggedIn() ? (
            <span className="user-icon" title="点击退出" onClick={handleLogout} style={{ cursor: 'pointer' }}>
              👤
            </span>
          ) : (
            <Link to="/login">登录</Link>
          )}
        </div>
      </div>
      <button
        className="mobile-menu-btn"
        type="button"
        aria-label="菜单"
        onClick={() => setMenuOpen(!menuOpen)}
      >
        {menuOpen ? '✕' : '☰'}
      </button>
    </header>
  );
}
