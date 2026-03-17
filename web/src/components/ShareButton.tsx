import { useState } from 'react';
import api from '../utils/api';
import { useToast } from '../utils/toast';

interface ShareModalProps {
  videoId: string;
  onClose: () => void;
}

function ShareModal({ videoId, onClose }: ShareModalProps) {
  const [shareUrl, setShareUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const { toast } = useToast();

  const generateLink = async () => {
    setLoading(true);
    try {
      const res = await api.get('/share/create', { params: { videoId } });
      const data = res.data.data as { shortCode: string };
      const url = `${window.location.origin}/s/${data.shortCode}`;
      setShareUrl(url);
    } catch {
      toast('生成分享链接失败', 'error');
    } finally {
      setLoading(false);
    }
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      toast('链接已复制', 'success');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast('复制失败', 'error');
    }
  };

  const shareToWeibo = () => {
    const url = encodeURIComponent(shareUrl);
    window.open(`https://service.weibo.com/share/share.php?url=${url}`, '_blank');
  };

  const shareToTwitter = () => {
    const url = encodeURIComponent(shareUrl);
    window.open(`https://twitter.com/intent/tweet?url=${url}`, '_blank');
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content share-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>分享视频</h3>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {!shareUrl ? (
            <button className="btn btn-primary" onClick={generateLink} disabled={loading}>
              {loading ? '生成中...' : '生成分享链接'}
            </button>
          ) : (
            <>
              <div className="share-url-row">
                <input
                  type="text"
                  readOnly
                  value={shareUrl}
                  className="share-url-input"
                />
                <button className="btn btn-primary" onClick={copyLink}>
                  {copied ? '已复制' : '复制'}
                </button>
              </div>
              <div className="share-social-buttons">
                <button className="btn btn-social btn-weibo" onClick={shareToWeibo}>
                  微博
                </button>
                <button className="btn btn-social btn-twitter" onClick={shareToTwitter}>
                  Twitter
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

interface ShareButtonProps {
  videoId: string;
}

export default function ShareButton({ videoId }: ShareButtonProps) {
  const [showModal, setShowModal] = useState(false);

  return (
    <>
      <button className="btn btn-share" onClick={() => setShowModal(true)}>
        分享
      </button>
      {showModal && (
        <ShareModal videoId={videoId} onClose={() => setShowModal(false)} />
      )}
    </>
  );
}
