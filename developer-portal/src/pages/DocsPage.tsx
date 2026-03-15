import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export default function DocsPage() {
  const { developer } = useAuth();
  const docsUrl = API_BASE ? `${API_BASE}/api/docs` : '/api/docs';

  return (
    <div className="docs-standalone">
      <header className="docs-header">
        <Link to="/" className="docs-logo">🎬 Video Platform</Link>
        <nav className="docs-nav">
          <Link to="/" className="btn btn-outline">首页</Link>
          {developer ? (
            <Link to="/dashboard" className="btn btn-primary">控制台</Link>
          ) : (
            <Link to="/login" className="btn btn-primary">登录</Link>
          )}
        </nav>
      </header>
      <iframe
        src={docsUrl}
        title="API Documentation"
        style={{
          width: '100%',
          height: 'calc(100vh - 56px)',
          border: 'none',
        }}
      />
    </div>
  );
}
