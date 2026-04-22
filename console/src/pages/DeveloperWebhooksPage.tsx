import { Navigate, useParams } from 'react-router-dom';

export default function DeveloperWebhooksPage() {
  const { appId } = useParams<{ appId: string }>();
  return <Navigate to={appId ? `/webhooks?appId=${appId}` : '/webhooks'} replace />;
}
