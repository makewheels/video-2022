import { useState, useEffect, useRef } from 'react';
import type { UserProfile, UploadCredentials } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';
import OSS from 'ali-oss';

export default function SettingsPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      const res = await api.get('/user/getMyProfile');
      const data = res.data.data as UserProfile;
      setProfile(data);
      setNickname(data.nickname || '');
      setBio(data.bio || '');
    } catch {
      toast('加载资料失败', 'error');
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await api.post('/user/updateProfile', {
        nickname: nickname.trim() || null,
        bio: bio.trim() || null,
      });
      toast('保存成功', 'success');
      loadProfile();
    } catch {
      toast('保存失败', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      toast('请选择图片文件', 'error');
      return;
    }

    // 验证文件大小 (最大 5MB)
    if (file.size > 5 * 1024 * 1024) {
      toast('图片大小不能超过 5MB', 'error');
      return;
    }

    setUploadProgress(0);
    setUploading(true);

    try {
      // 1. 创建头像文件记录
      const createRes = await api.get('/user/createAvatarFile');
      const { fileId } = createRes.data.data;

      // 2. 获取上传凭证
      const credRes = await api.get('/user/getAvatarUploadCredentials', {
        params: { fileId },
      });
      const creds = credRes.data.data as UploadCredentials;

      // 3. 上传到 OSS
      const client = new OSS({
        endpoint: creds.endpoint,
        accessKeyId: creds.accessKeyId,
        accessKeySecret: creds.secretKey,
        stsToken: creds.sessionToken,
        bucket: creds.bucket,
      });

      await client.multipartUpload(creds.key, file, {
        progress: (p: number) => setUploadProgress(Math.round(p * 100)),
      });

      // 4. 通知上传完成
      await api.get('/user/avatarUploadFinish', { params: { fileId } });

      toast('头像更新成功', 'success');
      loadProfile();
    } catch (err) {
      toast(err instanceof Error ? err.message : '上传失败', 'error');
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  if (!profile) return <div>加载中...</div>;

  return (
    <div className="page-container">
      <div className="card" style={{ maxWidth: 600, margin: '0 auto' }}>
        <div className="card-header">个人设置</div>
        <div style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ textAlign: 'center' }}>
            <div
              onClick={handleAvatarClick}
              style={{ cursor: 'pointer', position: 'relative', display: 'inline-block' }}
            >
              {profile.avatarUrl ? (
                <img
                  src={profile.avatarUrl}
                  alt="头像"
                  style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover' }}
                />
              ) : (
                <div style={{
                  width: 80, height: 80, borderRadius: '50%',
                  backgroundColor: 'var(--color-primary)',
                  color: '#fff', display: 'inline-flex',
                  alignItems: 'center', justifyContent: 'center',
                  fontSize: '2rem'
                }}>
                  {(nickname || profile.phone || '?')[0].toUpperCase()}
                </div>
              )}
              <div style={{
                position: 'absolute', bottom: 0, right: 0,
                backgroundColor: 'var(--color-primary)', color: '#fff',
                borderRadius: '50%', width: 24, height: 24,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: '0.8rem'
              }}>
                +
              </div>
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handleAvatarChange}
            />
            {uploading && (
              <div style={{ marginTop: 8 }}>
                <div className="progress-bar" style={{ width: 200, margin: '0 auto' }}>
                  <div className="progress-bar-fill" style={{ width: `${uploadProgress}%` }} />
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  上传中 {uploadProgress}%
                </div>
              </div>
            )}
            <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginTop: 4 }}>
              点击更换头像
            </div>
          </div>

          <div>
            <label className="form-label">昵称</label>
            <input
              type="text"
              className="form-input"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="输入昵称"
              maxLength={30}
            />
          </div>

          <div>
            <label className="form-label">个人简介</label>
            <textarea
              className="form-input"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="介绍一下自己..."
              maxLength={200}
              rows={3}
            />
            <div style={{ textAlign: 'right', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              {bio.length}/200
            </div>
          </div>

          <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}