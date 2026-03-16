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
  uploaderAvatarUrl?: string;
  uploaderId?: string;
  tags?: string[];
  category?: string;
}

export interface Comment {
  id: string;
  content: string;
  userPhone: string;
  userNickname?: string;
  userAvatarUrl?: string;
  createTime: string;
  likeCount: number;
  replyCount: number;
  parentId?: string;
  replyToUserPhone?: string;
  replyToUserNickname?: string;
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

export interface ChannelInfo {
  userId: string;
  nickname: string;
  avatarUrl?: string;
  bannerUrl?: string;
  bio?: string;
  subscriberCount: number;
  videoCount: number;
  isSubscribed: boolean;
}

export interface UserProfile {
  id: string;
  phone: string;
  nickname?: string;
  avatarUrl?: string;
  bannerUrl?: string;
  bio?: string;
}
