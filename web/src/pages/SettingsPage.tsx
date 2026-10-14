import { useSettingsPage } from './useSettingsPage';

export default function SettingsPage() {
  const {
    profile,
    nickname,
    setNickname,
    bio,
    setBio,
    saving,
    uploading,
    uploadProgress,
    fileInputRef,
    handleSave,
    handleAvatarClick,
    handleAvatarChange,
  } = useSettingsPage();

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
