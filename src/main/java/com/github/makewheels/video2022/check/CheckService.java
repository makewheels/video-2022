package com.github.makewheels.video2022.check;

import com.github.makewheels.video2022.etc.exception.VideoException;
import com.github.makewheels.video2022.playlist.bean.IdBean;
import com.github.makewheels.video2022.playlist.bean.Playlist;
import com.github.makewheels.video2022.redis.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口校验，如果校验不通过，抛异常
 */
@Service
public class CheckService {
    @Resource
    private CacheService cacheService;

    /**
     * 检查用户是否存在
     */
    public void checkUserExist(String userId) {
        if (cacheService.getUser(userId) == null) {
            throw new VideoException("用户不存在, userId = " + userId);
        }
    }

    /**
     * 检查视频是否存在
     */
    public void checkVideoExist(String videoId) {
        if (cacheService.getVideo(videoId) == null) {
            throw new VideoException("视频不存在, videoId = " + videoId);
        }
    }

    /**
     * 检查视频是否属于user
     */
    public void checkVideoBelongsToUser(String videoId, String userId) {
        checkVideoExist(videoId);
        checkUserExist(userId);

        String ownerId = cacheService.getVideo(videoId).getUserId();
        if (!ownerId.equals(userId)) {
            throw new VideoException("视频不属于user, videoId = " + videoId + ", " +
                    "ownerId = " + ownerId + ", userId = " + userId);
        }

    }

    /**
     * 检查播放列表是否存在
     */
    public void checkPlaylistExist(String playlistId) {
        if (cacheService.getPlaylist(playlistId) == null) {
            throw new VideoException("播放列表不存在, playlistId = " + playlistId);
        }
    }

    /**
     * 检查播放列表是否属于user
     */
    public void checkPlaylistOwner(String playlistId, String userId) {
        checkPlaylistExist(playlistId);
        checkUserExist(userId);

        String ownerId = cacheService.getPlaylist(playlistId).getOwnerId();
        if (!ownerId.equals(userId)) {
            throw new VideoException("playlist不属于user, playlistId = " + playlistId + ", " +
                    "ownerId = " + ownerId + ", userId = " + userId);
        }
    }

    public List<String> getPlaylistItemIds(Playlist playlist) {
        return playlist.getIdBeanList().stream()
                .map(IdBean::getPlaylistItemId).collect(Collectors.toList());
    }

    public List<String> getVideoIds(Playlist playlist) {
        return playlist.getIdBeanList().stream()
                .map(IdBean::getVideoId).collect(Collectors.toList());
    }

    public boolean containsPlaylistItemId(Playlist playlist, String playlistItemId) {
        return getVideoIds(playlist).contains(playlistItemId);
    }

    public boolean containsVideoId(Playlist playlist, String videoId) {
        return getVideoIds(playlist).contains(videoId);
    }

    public boolean isVideoBelongsToPlaylist(String videoId, String playlistId) {
        checkPlaylistExist(playlistId);
        checkVideoExist(videoId);

        Playlist playlist = cacheService.getPlaylist(playlistId);
        return containsVideoId(playlist, videoId);
    }

    /**
     * 检查商品是否属于不播放列表
     */
    public void checkVideoNotBelongsToPlaylist(String videoId, String playlistId) {
        if (isVideoBelongsToPlaylist(videoId, playlistId)) {
            throw new VideoException("视频已经在播放列表里, videoId = " + videoId + ", " +
                    "playlistId = " + playlistId);
        }
    }

    public void checkVideoBelongsToPlaylist(String videoId, String playlistId) {
        if (!isVideoBelongsToPlaylist(videoId, playlistId)) {
            throw new VideoException("视频不在播放列表里, videoId = " + videoId + ", " +
                    "playlistId = " + playlistId);
        }
    }
}
