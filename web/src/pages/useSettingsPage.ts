import { useState, useEffect, useRef, useCallback } from 'react';
import type { UserProfile, UploadCredentials } from '../types';
import api from '../utils/api';
import { useToast } from '../utils/toast';
import OSS from 'ali-oss';

export function useSettingsPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  const loadProfile = useCallback(async () => {
    try {
      const res = await api.get('/user/getMyProfile');
      const data = res.data.data as UserProfile;
      setProfile(data);
      setNickname(data.nickname || '');
      setBio(data.bio || '');
    } catch {
      toast('加载资料失败', 'error');
    }
  }, [toast]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

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

  return {
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
  };
}
