import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../utils/api';

export default function ShareRedirectPage() {
  const { shortCode } = useParams<{ shortCode: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (!shortCode) return;
    api.get('/share/stats', { params: { shortCode } })
      .then((res) => {
        const data = res.data.data as { videoId: string };
        navigate(`/watch/${data.videoId}`, { replace: true });
      })
      .catch(() => {
        navigate('/', { replace: true });
      });
  }, [shortCode, navigate]);

  return <div>跳转中...</div>;
}
