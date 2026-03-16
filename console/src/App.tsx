import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import AppsPage from './pages/AppsPage';
import DocsPage from './pages/DocsPage';
import StatsPage from './pages/StatsPage';
import WebhooksPage from './pages/WebhooksPage';
import DeveloperAppsPage from './pages/DeveloperAppsPage';
import DeveloperWebhooksPage from './pages/DeveloperWebhooksPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/docs" element={<DocsPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/apps" element={<AppsPage />} />
          <Route path="/stats" element={<StatsPage />} />
          <Route path="/webhooks" element={<WebhooksPage />} />
          <Route path="/developer-apps" element={<DeveloperAppsPage />} />
          <Route path="/developer-apps/:appId/webhooks" element={<DeveloperWebhooksPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
