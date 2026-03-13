import { useState, useEffect } from 'react';
import type { UserProfile } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';

export default function SettingsPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [saving, setSaving] = useState(false);
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

  if (!profile) return <div>加载中...</div>;

  return (
    <div className="page-container">
      <div className="card" style={{ maxWidth: 600, margin: '0 auto' }}>
        <div className="card-header">个人设置</div>
        <div style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ textAlign: 'center' }}>
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
