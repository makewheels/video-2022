export interface Video {
  id: string;
  watchId: string;
  title: string;
  description: string;
  status: string;
  visibility: 'PUBLIC' | 'UNLISTED' | 'PRIVATE';
  watchCount: number;
  duration: number;
  createTimeString: string;
  createTime: string;
  watchUrl: string;
  shortUrl?: string;
  type: string;
  coverUrl?: string;
  youtubePublishTimeString?: string;
  uploaderName?: string;
}

export interface Comment {
  id: string;
  content: string;
  userPhone: string;
  createTime: string;
  likeCount: number;
  replyCount: number;
  parentId?: string;
  replyToUserPhone?: string;
}

export interface PlaylistItem {
  videoId: string;
  watchId: string;
  title: string;
  coverUrl: string;
  watchCount: number;
  videoCreateTime: string;
}

export interface Playlist {
  id: string;
  title: string;
}

export interface WatchInfo {
  videoId: string;
  coverUrl: string;
  videoStatus: string;
  multivariantPlaylistUrl: string;
  progressInMillis: number;
}

export interface LikeStatus {
  likeCount: number;
  userAction: 'LIKE' | 'DISLIKE' | 'NONE';
}

export interface UploadCredentials {
  key: string;
  bucket: string;
  endpoint: string;
  accessKeyId: string;
  secretKey: string;
  sessionToken: string;
}
