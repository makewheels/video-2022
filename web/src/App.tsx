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
import ChannelPage from './pages/ChannelPage';
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

        <Route element={<PrivateRoute />}>
          <Route path="/my-videos" element={<MyVideosPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/edit/:videoId" element={<EditPage />} />
          <Route path="/statistics" element={<StatisticsPage />} />
          <Route path="/youtube" element={<YouTubePage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
