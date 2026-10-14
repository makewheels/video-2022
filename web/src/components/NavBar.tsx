import { Link } from 'react-router-dom';
import NotificationBell from './NotificationBell';
import { isLoggedIn } from '../utils/auth';
import { useNavBar } from './useNavBar';

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/my-videos', label: '我的视频' },
  { to: '/upload', label: '上传' },
  { to: '/chat', label: 'AI 助手' },
  { to: '/statistics', label: '统计' },
  { to: '/youtube', label: 'YouTube' },
];

export default function NavBar() {
  const {
    location,
    theme,
    menuOpen,
    setMenuOpen,
    searchQuery,
    setSearchQuery,
    userInfo,
    dropdownOpen,
    setDropdownOpen,
    dropdownRef,
    handleToggleTheme,
    handleLogout,
    handleSearch,
  } = useNavBar();

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
        <NotificationBell />
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
