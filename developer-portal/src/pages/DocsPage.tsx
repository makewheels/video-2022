const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export default function DocsPage() {
  const docsUrl = API_BASE ? `${API_BASE}/api/docs` : '/api/docs';

  return (
    <div className="page docs-page">
      <iframe
        src={docsUrl}
        title="API Documentation"
        style={{
          width: '100%',
          height: 'calc(100vh - 40px)',
          border: 'none',
          borderRadius: '8px',
        }}
      />
    </div>
  );
}
