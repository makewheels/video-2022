import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { isLoggedIn, removeToken } from '../utils/auth';
import { toggleTheme, getEffectiveTheme } from '../utils/theme';

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/my-videos', label: '我的视频' },
  { to: '/upload', label: '上传' },
  { to: '/statistics', label: '统计' },
  { to: '/youtube', label: 'YouTube' },
];

export default function NavBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [theme, setThemeState] = useState(getEffectiveTheme);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState(searchParams.get('keyword') || '');

  const handleToggleTheme = () => {
    const next = toggleTheme();
    setThemeState(next);
  };

  const handleLogout = () => {
    removeToken();
    navigate('/login');
  };

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    const q = searchQuery.trim();
    if (q) {
      navigate(`/?keyword=${encodeURIComponent(q)}`);
    } else {
      navigate('/');
    }
    setMenuOpen(false);
  };

  return (
    <header className="page-header">
      <Link to="/" className="logo">📹 Video</Link>

      <form className="nav-search" onSubmit={handleSearch}>
        <input
          type="text"
          className="nav-search-input"
          placeholder="搜索视频..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
        <button type="submit" className="nav-search-btn" title="搜索">🔍</button>
      </form>

      <nav className={`nav-menu${menuOpen ? ' open' : ''}`}>
        {navLinks.map(({ to, label }) => {
          const isActive = to === '/'
            ? location.pathname === '/'
            : location.pathname.startsWith(to);
          return (
            <Link
              key={to}
              to={to}
              className={`nav-link${isActive ? ' active' : ''}`}
              onClick={() => setMenuOpen(false)}
            >
              {label}
            </Link>
          );
        })}
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
