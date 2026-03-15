import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function DocsPage() {
  const { developer } = useAuth();
  const base = import.meta.env.BASE_URL || '/developer-portal/';
  const docsUrl = `${base}api-docs.html`;

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
