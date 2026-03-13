import { useState, useEffect, useRef } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { isLoggedIn, removeToken } from '../utils/auth';
import { toggleTheme, getEffectiveTheme } from '../utils/theme';
import api from '../utils/api';

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/my-videos', label: '我的视频' },
  { to: '/upload', label: '上传' },
  { to: '/statistics', label: '统计' },
  { to: '/youtube', label: 'YouTube' },
];

interface UserInfo {
  id: string;
  nickname?: string;
  avatarUrl?: string;
}

export default function NavBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [theme, setThemeState] = useState(getEffectiveTheme);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState(searchParams.get('keyword') || '');
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isLoggedIn()) {
      api.get('/user/getMyProfile')
        .then(res => {
          const data = res.data.data as UserInfo;
          setUserInfo(data);
        })
        .catch(() => {});
    }
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleToggleTheme = () => {
    const next = toggleTheme();
    setThemeState(next);
  };

  const handleLogout = () => {
    removeToken();
    setUserInfo(null);
    setDropdownOpen(false);
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
            <div className="user-dropdown" ref={dropdownRef}>
              <button
                className="user-avatar-btn"
                type="button"
                onClick={() => setDropdownOpen(!dropdownOpen)}
              >
                {userInfo?.avatarUrl ? (
                  <img src={userInfo.avatarUrl} alt="" className="user-avatar-img" />
                ) : (
                  <span className="user-avatar-placeholder">
                    {(userInfo?.nickname || '👤')[0]}
                  </span>
                )}
              </button>
              {dropdownOpen && (
                <div className="user-dropdown-menu">
                  {userInfo?.id && (
                    <Link
                      to={`/channel/${userInfo.id}`}
                      className="dropdown-item"
                      onClick={() => setDropdownOpen(false)}
                    >
                      我的频道
                    </Link>
                  )}
                  <Link
                    to="/settings"
                    className="dropdown-item"
                    onClick={() => setDropdownOpen(false)}
                  >
                    设置
                  </Link>
                  <button className="dropdown-item" onClick={handleLogout}>
                    退出
                  </button>
                </div>
              )}
            </div>
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
