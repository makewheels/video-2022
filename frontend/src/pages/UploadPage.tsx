import { useState, useEffect, useCallback, useRef, type DragEvent, type ChangeEvent, type RefObject } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Playlist, UploadCredentials } from '../types';
import OSS from 'ali-oss';
import api from '../utils/api';
import { requireAuth } from '../utils/auth';
import { useToast } from '../utils/toast';

interface CreateVideoResponse {
  fileId: string;
  videoId: string;
  watchId: string;
  watchUrl: string;
}

const STATUS_LABELS: Record<string, string> = {
  CREATED: '已创建',
  UPLOADING: '上传中',
  PREPARE_TRANSCODING: '准备转码',
  TRANSCODING: '转码中',
  TRANSCODING_PARTLY_COMPLETE: '转码中（部分完成）',
  PROCESSING_AFTER_TRANSCODE_COMPLETE: '转码后处理中',
  READY: '✅ 处理完成',
};

const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024;

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

async function performOSSUpload(
  creds: UploadCredentials,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  const client = new OSS({
    endpoint: creds.endpoint,
    accessKeyId: creds.accessKeyId,
    accessKeySecret: creds.secretKey,
    stsToken: creds.sessionToken,
    bucket: creds.bucket,
  });
  await client.multipartUpload(creds.key, file, {
    parallel: 2,
    partSize: 512 * 1024,
    progress: (p) => onProgress(Math.round(p * 100)),
  });
}

async function doUpload(
  file: File,
  setProgress: (n: number) => void,
  toast: (msg: string, type?: 'info' | 'success' | 'error') => void,
): Promise<CreateVideoResponse | null> {
  try {
    const createRes = await api.post('/video/create', {
      rawFilename: file.name,
      size: file.size,
      videoType: 'USER_UPLOAD',
    });
    const data = createRes.data.data as CreateVideoResponse;
    const credRes = await api.get('/file/getUploadCredentials', {
      params: { fileId: data.fileId },
    });
    const creds = credRes.data.data as UploadCredentials;
    await performOSSUpload(creds, file, setProgress);
    await api.get('/file/uploadFinish', { params: { fileId: data.fileId } });
    await api.get('/video/rawFileUploadFinish', { params: { videoId: data.videoId } });
    toast('上传完成', 'success');
    return data;
  } catch (err) {
    toast(err instanceof Error ? err.message : '上传失败', 'error');
    return null;
  }
}

function startStatusPolling(
  videoId: string,
  setVideoStatus: (s: string) => void,
  pollingRef: { current: ReturnType<typeof setInterval> | null },
): void {
  if (pollingRef.current) clearInterval(pollingRef.current);
  pollingRef.current = setInterval(async () => {
    try {
      const res = await api.get('/video/getVideoStatus', { params: { videoId } });
      const d = res.data.data as { status: string; isReady?: boolean };
      setVideoStatus(d.status);
      if (d.isReady && pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    } catch {
      // ignore polling errors
    }
  }, 5000);
}

async function loadMyPlaylists(
  setPlaylists: (p: Playlist[]) => void,
  setSelectedPlaylistId: (s: string) => void,
): Promise<void> {
  try {
    const res = await api.get('/playlist/getMyPlaylistByPage', {
      params: { skip: 0, limit: 100 },
    });
    const list = res.data.data as Playlist[];
    setPlaylists(list);
    if (list.length > 0) setSelectedPlaylistId(list[0].id);
  } catch {
    // ignore
  }
}

async function updateVideoInfo(
  videoId: string,
  title: string,
  description: string,
  visibility: string,
  toast: (msg: string, type?: 'info' | 'success' | 'error') => void,
): Promise<void> {
  try {
    await api.post('/video/updateInfo', { id: videoId, title, description, visibility });
    toast('信息已更新', 'success');
  } catch (err) {
    toast(err instanceof Error ? err.message : '更新失败', 'error');
  }
}

async function copyVideoInfo(
  title: string,
  watchUrl: string,
  toast: (msg: string, type?: 'info' | 'success' | 'error') => void,
): Promise<void> {
  try {
    await navigator.clipboard.writeText(title + '\n' + watchUrl);
    toast('已复制到剪贴板', 'success');
  } catch {
    toast('复制失败', 'error');
  }
}

async function addToPlaylist(
  playlistId: string,
  videoId: string,
  toast: (msg: string, type?: 'info' | 'success' | 'error') => void,
): Promise<void> {
  try {
    await api.post('/playlist/addPlaylistItem', {
      playlistId,
      videoIdList: [videoId],
      addMode: 'ADD_TO_TOP',
    });
    toast('已加入播放列表', 'success');
  } catch (err) {
    toast(err instanceof Error ? err.message : '加入播放列表失败', 'error');
  }
}

interface DropZoneProps {
  file: File | null;
  uploading: boolean;
  progress: number;
  fileInputRef: RefObject<HTMLInputElement | null>;
  onFileSelect: (f: File) => void;
  onUpload: () => void;
}

function DropZone({ file, uploading, progress, fileInputRef, onFileSelect, onUpload }: DropZoneProps) {
  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    const f = e.dataTransfer.files[0];
    if (f) onFileSelect(f);
  };
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) onFileSelect(f);
  };
  return (
    <div className="upload-section">
      <div className="upload-zone" onDragOver={(e) => e.preventDefault()} onDrop={handleDrop} onClick={() => fileInputRef.current?.click()}>
        <div className="upload-zone-icon">📁</div>
        <div className="upload-zone-text">
          {file ? `${file.name} (${formatFileSize(file.size)})` : '拖拽文件到此处，或点击选择文件'}
        </div>
      </div>
      <input ref={fileInputRef} type="file" accept="video/*,audio/*" style={{ display: 'none' }} onChange={handleChange} />
      {file && !uploading && (
        <button className="btn btn-primary" onClick={onUpload} style={{ marginTop: 12 }}>开始上传</button>
      )}
      {uploading && (
        <div style={{ marginTop: 12 }}>
          <div className="progress-bar">
            <div className="progress-bar-fill" style={{ width: `${progress}%` }} />
          </div>
          <div className="progress-text">{progress}%</div>
        </div>
      )}
    </div>
  );
}

function StatusDisplay({ videoStatus }: { videoStatus: string }) {
  if (!videoStatus) return null;
  return (
    <div className="upload-section">
      <div className="section-title">处理状态</div>
      <p>{STATUS_LABELS[videoStatus] || videoStatus}</p>
    </div>
  );
}

interface EditFormProps {
  title: string;
  description: string;
  visibility: string;
  onTitleChange: (v: string) => void;
  onDescriptionChange: (v: string) => void;
  onVisibilityChange: (v: string) => void;
  onUpdateInfo: () => void;
  onCopy: () => void;
}

function EditForm(props: EditFormProps) {
  const { title, description, visibility } = props;
  const { onTitleChange, onDescriptionChange, onVisibilityChange, onUpdateInfo, onCopy } = props;
  return (
    <div className="upload-section">
      <div className="section-title">视频信息</div>
      <div className="form-group">
        <input className="form-input" value={title} onChange={(e) => onTitleChange(e.target.value)} placeholder="标题" />
      </div>
      <div className="form-group">
        <textarea className="form-input" value={description} onChange={(e) => onDescriptionChange(e.target.value)} placeholder="描述" rows={4} />
      </div>
      <div className="form-group">
        <select className="form-input" value={visibility} onChange={(e) => onVisibilityChange(e.target.value)}>
          <option value="PUBLIC">公开</option>
          <option value="UNLISTED">不列出</option>
          <option value="PRIVATE">私密</option>
        </select>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <button className="btn btn-primary" onClick={onUpdateInfo}>修改信息</button>
        <button className="btn btn-secondary" onClick={onCopy}>一键复制</button>
      </div>
    </div>
  );
}

interface PlaylistManagerProps {
  playlists: Playlist[];
  selectedPlaylistId: string;
  onSelectPlaylist: (id: string) => void;
  onAddToPlaylist: () => void;
}

function PlaylistManager({ playlists, selectedPlaylistId, onSelectPlaylist, onAddToPlaylist }: PlaylistManagerProps) {
  if (playlists.length === 0) return null;
  return (
    <div className="upload-section">
      <div className="section-title">播放列表</div>
      <div style={{ display: 'flex', gap: 8 }}>
        <select className="form-input" value={selectedPlaylistId} onChange={(e) => onSelectPlaylist(e.target.value)} style={{ flex: 1 }}>
          {playlists.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}
        </select>
        <button className="btn btn-primary" onClick={onAddToPlaylist}>加入播放列表</button>
      </div>
    </div>
  );
}

function useUploadState() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [videoData, setVideoData] = useState<CreateVideoResponse | null>(null);
  const [videoStatus, setVideoStatus] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] = useState('PUBLIC');
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [selectedPlaylistId, setSelectedPlaylistId] = useState('');

  useEffect(() => { requireAuth(navigate); }, [navigate]);
  useEffect(() => () => { if (pollingRef.current) clearInterval(pollingRef.current); }, []);

  const handleFileSelect = useCallback((f: File) => {
    if (f.size > MAX_FILE_SIZE) { toast('文件大小不能超过 5GB', 'error'); return; }
    setFile(f);
    setTitle(f.name.replace(/\.[^/.]+$/, ''));
  }, [toast]);

  const handleUpload = useCallback(async () => {
    if (!file) return;
    setUploading(true);
    setProgress(0);
    const result = await doUpload(file, setProgress, toast);
    setUploading(false);
    if (result) {
      setVideoData(result);
      startStatusPolling(result.videoId, setVideoStatus, pollingRef);
      loadMyPlaylists(setPlaylists, setSelectedPlaylistId);
    }
  }, [file, toast]);

  return {
    file, uploading, progress, fileInputRef, videoData, videoStatus,
    title, setTitle, description, setDescription, visibility, setVisibility,
    playlists, selectedPlaylistId, setSelectedPlaylistId,
    handleFileSelect, handleUpload, toast,
  };
}

function UploadPage() {
  const state = useUploadState();

  return (
    <div className="card">
      <div className="card-header">上传视频</div>
      <DropZone
        file={state.file} uploading={state.uploading} progress={state.progress}
        fileInputRef={state.fileInputRef} onFileSelect={state.handleFileSelect} onUpload={state.handleUpload}
      />
      {state.videoData && (
        <>
          <StatusDisplay videoStatus={state.videoStatus} />
          <EditForm
            title={state.title} description={state.description} visibility={state.visibility}
            onTitleChange={state.setTitle} onDescriptionChange={state.setDescription}
            onVisibilityChange={state.setVisibility}
            onUpdateInfo={() => updateVideoInfo(state.videoData!.videoId, state.title, state.description, state.visibility, state.toast)}
            onCopy={() => copyVideoInfo(state.title, state.videoData!.watchUrl, state.toast)}
          />
          <PlaylistManager
            playlists={state.playlists} selectedPlaylistId={state.selectedPlaylistId}
            onSelectPlaylist={state.setSelectedPlaylistId}
            onAddToPlaylist={() => addToPlaylist(state.selectedPlaylistId, state.videoData!.videoId, state.toast)}
          />
        </>
      )}
    </div>
  );
}

export default UploadPage;
