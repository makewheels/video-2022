import { useState, useEffect, useRef } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { isLoggedIn, removeToken } from '../utils/auth';
import { toggleTheme, getEffectiveTheme } from '../utils/theme';
import api from '../utils/api';

interface UserInfo {
  id: string;
  nickname?: string;
  avatarUrl?: string;
}

export function useNavBar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [theme, setThemeState] = useState(getEffectiveTheme);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || searchParams.get('keyword') || '');
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
      navigate(`/search?q=${encodeURIComponent(q)}`);
    } else {
      navigate('/');
    }
    setMenuOpen(false);
  };

  return {
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
  };
}
