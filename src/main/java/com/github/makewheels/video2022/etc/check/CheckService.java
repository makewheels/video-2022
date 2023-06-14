package com.github.makewheels.video2022.etc.check;

import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.request.add.AddMode;
import com.github.makewheels.video2022.playlist.item.request.delete.DeleteMode;
import com.github.makewheels.video2022.playlist.item.request.move.MoveMode;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.Visibility;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口校验，如果校验不通过，抛异常
 */
@Service
public class CheckService {
    @Resource
    private CacheService cacheService;
    @Resource
    private UserRepository userRepository;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private PlaylistRepository playlistRepository;
    @Resource
    private FileRepository fileRepository;

    /**
     * 检查用户是否存在
     */
    public void checkUserExist(String userId) {
        if (!userRepository.isUserExist(userId)) {
            throw new VideoException("用户不存在, userId = " + userId);
        }
    }

    /**
     * 检查用户是否存在当前请求线程
     */
    public void checkUserHolderExist() {
        if (UserHolder.get() == null) {
            throw new VideoException("用户UserHolder不存在");
        }
    }

    /**
     * 检查视频是否存在
     */
    public void checkVideoExist(String videoId) {
        if (!videoRepository.isVideoExist(videoId)) {
            throw new VideoException("视频不存在, videoId = " + videoId);
        }
    }

    /**
     * 检查视频是否属于user
     */
    public void checkVideoBelongsToUser(String videoId, String userId) {
        checkVideoExist(videoId);
        checkUserExist(userId);

        Video video = videoRepository.getById(videoId);
        String ownerId = video.getUploaderId();
        if (!ownerId.equals(userId)) {
            throw new VideoException("视频不属于user, videoId = " + videoId + ", " +
                    "ownerId = " + ownerId + ", userId = " + userId);
        }
    }

    /**
     * 检查播放列表是否存在
     */
    public void checkPlaylistExist(String playlistId) {
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        if (playlist == null) {
            throw new VideoException("播放列表不存在, playlistId = " + playlistId);
        } else if (playlist.getIsDelete()) {
            throw new VideoException("播放列表已删除, playlistId = " + playlistId);
        }
    }

    /**
     * 检查播放列表是否属于user
     */
    public void checkPlaylistOwner(String playlistId, String userId) {
        checkPlaylistExist(playlistId);
        checkUserExist(userId);

        String ownerId = playlistRepository.getPlaylist(playlistId).getOwnerId();
        if (!ownerId.equals(userId)) {
            throw new VideoException("playlist不属于user, playlistId = " + playlistId + ", " +
                    "ownerId = " + ownerId + ", userId = " + userId);
        }
    }

    public void checkPlaylistCanDelete(String playlistId, String userId) {
        checkUserExist(userId);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        if (playlist == null) {
            throw new VideoException("播放列表不存在, playlistId = " + playlistId);
        }
        String ownerId = playlist.getOwnerId();
        if (!ownerId.equals(userId)) {
            throw new VideoException("playlist不属于user, playlistId = " + playlistId + ", " +
                    "ownerId = " + ownerId + ", userId = " + userId);
        }

        if (!playlist.getIsDelete()) {
            throw new VideoException("播放列表状态正常, 不能恢复, playlistId = " + playlistId);
        }

    }

    public List<String> getVideoIds(Playlist playlist) {
        List<IdBean> idBeanList = playlist.getVideoList();
        if (idBeanList == null) {
            return new ArrayList<>();
        }
        return idBeanList.stream()
                .map(IdBean::getVideoId).collect(Collectors.toList());
    }

    public boolean containsPlayItemId(Playlist playlist, String playlistItemId) {
        return getVideoIds(playlist).contains(playlistItemId);
    }

    public boolean containsVideoId(Playlist playlist, String videoId) {
        return getVideoIds(playlist).contains(videoId);
    }

    /**
     * 判断播放列表是否属于user
     */
    public boolean isVideoBelongsToPlaylist(String playlistId, String videoId) {
        checkPlaylistExist(playlistId);
        checkVideoExist(videoId);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        return containsVideoId(playlist, videoId);
    }

    /**
     * 检查视频是否属于不播放列表
     */
    public void checkVideoNotBelongToPlaylist(String playlistId, List<String> videoIdList) {
        for (String videoId : videoIdList) {
            if (isVideoBelongsToPlaylist(playlistId, videoId)) {
                throw new VideoException("视频已经在播放列表里, videoId = " + videoId + ", " +
                        "playlistId = " + playlistId);
            }
        }
    }

    /**
     * 检查视频是否属于播放列表
     */
    public void checkVideoBelongToPlaylist(String playlistId, String videoId) {
        if (!isVideoBelongsToPlaylist(playlistId, videoId)) {
            throw new VideoException("视频不在播放列表里, videoId = " + videoId + ", " +
                    "playlistId = " + playlistId);
        }
    }

    /**
     * 检查视频是否属于播放列表
     */
    public void checkVideoBelongToPlaylist(String playlistId, List<String> videoIdList) {
        for (String videoId : videoIdList) {
            if (!isVideoBelongsToPlaylist(playlistId, videoId)) {
                throw new VideoException("视频不在播放列表里, videoId = " + videoId + ", " +
                        "playlistId = " + playlistId);
            }
        }
    }

    /**
     * 检查visibility是否合法
     */
    public void checkVisibility(String visibility) {
        if (!StringUtils.equalsAny(visibility, Visibility.ALL)) {
            throw new VideoException("visibility不合法, visibility = " + visibility);
        }
    }

    /**
     * 检查addMode是否合法
     */
    public void checkAddMode(String addMode) {
        if (!StringUtils.equalsAny(addMode, AddMode.ALL)) {
            throw new VideoException("addMode不合法, addMode = " + addMode);
        }
    }

    /**
     * 检查deleteMode是否合法
     */
    public void checkDeletePlayItemMode(String deleteMode) {
        if (!StringUtils.equalsAny(deleteMode, DeleteMode.ALL)) {
            throw new VideoException("deleteMode不合法, deleteMode = " + deleteMode);
        }
    }

    /**
     * 检查moveMode是否合法
     */
    public void checkMoveMode(String moveMode) {
        if (!StringUtils.equalsAny(moveMode, MoveMode.ALL)) {
            throw new VideoException("mode不合法, mode = " + moveMode);
        }
    }

    /**
     * 检查文件是否存在
     */
    public void checkFileExist(String fileId) {
        if (!fileRepository.isFileExist(fileId)) {
            throw new VideoException("文件不存在, fileId = " + fileId);
        }
    }

    /**
     * 检查文件属于用户
     */
    public void checkFileBelongsToUserHolder(String fileId) {
        checkFileExist(fileId);
        checkUserHolderExist();

        String userHolderUserId = UserHolder.getUserId();
        String fileUserId = fileRepository.getUserIdByFileId(fileId);

        if (!StringUtils.equals(userHolderUserId, fileUserId)) {
            throw new VideoException("文件和用户不匹配, " +
                    "user = " + userHolderUserId + ", fileId = " + fileId);
        }
    }
}
