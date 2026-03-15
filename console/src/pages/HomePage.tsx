import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function HomePage() {
  const { developer } = useAuth();

  return (
    <div className="home-page">
      <header className="home-header">
        <div className="home-header-inner">
          <h1>🎬 Video Platform</h1>
          <div className="home-header-actions">
            {developer ? (
              <Link to="/dashboard" className="btn btn-primary">进入控制台</Link>
            ) : (
              <>
                <Link to="/login" className="btn btn-outline">登录</Link>
                <Link to="/register" className="btn btn-primary">注册</Link>
              </>
            )}
          </div>
        </div>
      </header>

      <section className="hero">
        <h2>开放 API 开发者平台</h2>
        <p>接入视频平台能力，构建你自己的应用</p>
        <Link to={developer ? '/dashboard' : '/register'} className="btn btn-primary btn-lg">
          开始使用 →
        </Link>
        <Link to="/docs" className="btn btn-outline btn-lg" style={{ marginLeft: '12px' }}>
          📖 API 文档
        </Link>
      </section>

      <section className="features">
        <div className="feature-grid">
          <div className="feature-card">
            <div className="feature-icon">🎥</div>
            <h3>视频管理</h3>
            <p>上传、查询、管理视频资源，支持批量操作</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">⚡</div>
            <h3>自动转码</h3>
            <p>智能转码，支持多种分辨率和编码格式</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">🔔</div>
            <h3>Webhook 通知</h3>
            <p>实时接收视频处理状态变更通知</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">📖</div>
            <h3>完整文档</h3>
            <p>详细的 API 文档和 Swagger 交互式调试</p>
          </div>
        </div>
      </section>

      <footer className="home-footer">
        <p>Video Platform Developer Portal &copy; {new Date().getFullYear()}</p>
      </footer>
    </div>
  );
}
