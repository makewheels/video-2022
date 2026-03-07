import { useState, useEffect } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import type { Video } from '../types';
import api from '../utils/api';
import { requireAuth } from '../utils/auth';
import { useToast } from '../utils/toast';
import ConfirmDialog from '../components/ConfirmDialog';

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'UNLISTED', label: '不公开' },
  { value: 'PRIVATE', label: '私有' },
] as const;

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function formatType(type: string): string {
  if (type === 'USER_UPLOAD') return '用户上传';
  if (type === 'YOUTUBE') return 'YouTube搬运';
  return type;
}

function copyToClipboard(text: string, toast: (msg: string, type?: 'info' | 'success' | 'error') => void) {
  navigator.clipboard.writeText(text).then(
    () => toast('已复制到剪贴板', 'success'),
    () => toast('复制失败', 'error'),
  );
}

function EditPage() {
  const { videoId } = useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [video, setVideo] = useState<Video | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState<'PUBLIC' | 'UNLISTED' | 'PRIVATE'>('PUBLIC');
  const [saving, setSaving] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  useEffect(() => {
    requireAuth(navigate);
  }, [navigate]);

  useEffect(() => {
    if (!videoId) return;
    api.get('/video/getVideoDetail', { params: { videoId } })
      .then((res) => {
        const v = res.data.data as Video;
        setVideo(v);
        setTitle(v.title);
        setDescription(v.description || '');
        setVisibility(v.visibility);
      })
      .catch(() => toast('加载视频信息失败', 'error'));
  }, [videoId, toast]);

  const handleSave = async () => {
    if (!video) return;
    if (title === video.title && description === (video.description || '') && visibility === video.visibility) {
      toast('没有修改', 'info');
      return;
    }
    setSaving(true);
    try {
      await api.post('/video/updateInfo', { id: videoId, title, description, visibility });
      toast('保存成功', 'success');
      setVideo({ ...video, title, description, visibility });
    } catch {
      toast('保存失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    try {
      await api.get('/video/delete', { params: { videoId } });
      toast('删除成功', 'success');
      navigate('/');
    } catch {
      toast('删除失败', 'error');
    } finally {
      setShowDeleteDialog(false);
    }
  };

  if (!video) {
    return <div className="page-container">加载中...</div>;
  }

  return (
    <div className="page-container edit-page">
      <div className="edit-header">
        <Link to="/" className="back-link">← 返回列表</Link>
      </div>

      <InfoCard video={video} toast={toast} />
      <FormCard
        title={title}
        description={description}
        visibility={visibility}
        saving={saving}
        onTitleChange={setTitle}
        onDescriptionChange={setDescription}
        onVisibilityChange={setVisibility}
        onSave={handleSave}
      />
      <DeleteSection onDelete={() => setShowDeleteDialog(true)} />

      <ConfirmDialog
        open={showDeleteDialog}
        title="确认删除"
        message="确定要删除这个视频吗？此操作不可撤销。"
        onConfirm={confirmDelete}
        onCancel={() => setShowDeleteDialog(false)}
      />
    </div>
  );
}

function InfoCard(props: {
  video: Video;
  toast: (msg: string, type?: 'info' | 'success' | 'error') => void;
}) {
  const { video, toast } = props;
  return (
    <div className="edit-info-card card">
      <h3 className="card-header">视频信息</h3>
      <div className="info-grid">
        <div className="info-item">
          <span className="info-label">播放次数</span>
          <span className="info-value">{video.watchCount}</span>
        </div>
        <div className="info-item">
          <span className="info-label">时长</span>
          <span className="info-value">{formatDuration(video.duration)}</span>
        </div>
        <div className="info-item">
          <span className="info-label">创建时间</span>
          <span className="info-value">{video.createTimeString}</span>
        </div>
        <div className="info-item">
          <span className="info-label">类型</span>
          <span className="info-value">{formatType(video.type)}</span>
        </div>
      </div>
      <div className="info-links">
        <div className="info-link-row">
          <span className="info-label">观看链接</span>
          <span className="link-value">{video.watchUrl}</span>
          <button className="copy-btn" onClick={() => copyToClipboard(video.watchUrl, toast)}>复制</button>
        </div>
        {video.shortUrl && (
          <div className="info-link-row">
            <span className="info-label">短链接</span>
            <span className="link-value">{video.shortUrl}</span>
            <button className="copy-btn" onClick={() => copyToClipboard(video.shortUrl!, toast)}>复制</button>
          </div>
        )}
      </div>
    </div>
  );
}

function FormCard(props: {
  title: string;
  description: string;
  visibility: string;
  saving: boolean;
  onTitleChange: (v: string) => void;
  onDescriptionChange: (v: string) => void;
  onVisibilityChange: (v: 'PUBLIC' | 'UNLISTED' | 'PRIVATE') => void;
  onSave: () => void;
}) {
  const { title, description, visibility, saving, onTitleChange, onDescriptionChange, onVisibilityChange, onSave } = props;
  return (
    <div className="edit-form-card card">
      <h3 className="card-header">编辑信息</h3>
      <div className="form-group">
        <label className="form-label">标题</label>
        <input
          className="form-input"
          type="text"
          value={title}
          onChange={(e) => onTitleChange(e.target.value)}
        />
      </div>
      <div className="form-group">
        <label className="form-label">简介</label>
        <textarea
          className="form-textarea"
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          rows={4}
        />
      </div>
      <div className="form-group">
        <label className="form-label">可见性</label>
        <select
          className="form-input"
          value={visibility}
          onChange={(e) => onVisibilityChange(e.target.value as 'PUBLIC' | 'UNLISTED' | 'PRIVATE')}
        >
          {VISIBILITY_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>
      <div className="edit-actions">
        <button onClick={onSave} disabled={saving}>
          {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  );
}

function DeleteSection(props: { onDelete: () => void }) {
  return (
    <div className="card" style={{ borderColor: 'red' }}>
      <h3 className="card-header">危险操作</h3>
      <p>删除视频后无法恢复，请谨慎操作。</p>
      <button style={{ color: 'red' }} onClick={props.onDelete}>删除视频</button>
    </div>
  );
}

export default EditPage;
