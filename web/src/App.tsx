import { Routes, Route, Navigate, Outlet, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import AuthCallback from './pages/AuthCallback';
import HomePage from './pages/HomePage';
import MyVideosPage from './pages/MyVideosPage';
import UploadPage from './pages/UploadPage';
import EditPage from './pages/EditPage';
import WatchPage from './pages/WatchPage';
import StatisticsPage from './pages/StatisticsPage';
import YouTubePage from './pages/YouTubePage';
import SettingsPage from './pages/SettingsPage';
import WatchHistoryPage from './pages/WatchHistoryPage';
import ChannelPage from './pages/ChannelPage';
import SearchPage from './pages/SearchPage';
import ShareRedirectPage from './pages/ShareRedirectPage';
import ChatPage from './pages/ChatPage';
import { isLoggedIn } from './utils/auth';

function PrivateRoute() {
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoggedIn()) {
      const target = encodeURIComponent(window.location.pathname + window.location.search);
      navigate(`/login?target=${target}`, { replace: true });
    }
  }, [navigate]);

  if (!isLoggedIn()) return null;
  return <Outlet />;
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallback />} />
        <Route path="/watch/:videoId" element={<WatchPage />} />
        <Route path="/channel/:userId" element={<ChannelPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/s/:shortCode" element={<ShareRedirectPage />} />
        <Route path="/chat" element={<ChatPage />} />

        <Route element={<PrivateRoute />}>
          <Route path="/my-videos" element={<MyVideosPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/edit/:videoId" element={<EditPage />} />
          <Route path="/statistics" element={<StatisticsPage />} />
          <Route path="/youtube" element={<YouTubePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/watch-history" element={<WatchHistoryPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
