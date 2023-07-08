package com.github.makewheels.video2022.etc.check;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.FileRepository;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.request.add.AddMode;
import com.github.makewheels.video2022.playlist.item.request.delete.DeleteMode;
import com.github.makewheels.video2022.playlist.item.request.move.MoveMode;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.springboot.exception.VideoException;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserRepository;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.dto.CreateVideoDTO;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
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
            throw new VideoException(ErrorCode.USER_NOT_EXIST, "用户不存在, userId = " + userId);
        }
    }

    /**
     * 检查用户是否存在当前请求线程
     */
    public void checkUserHolderExist() {
        if (UserHolder.get() == null) {
            throw new VideoException(ErrorCode.USER_NOT_LOGIN, "用户UserHolder不存在");
        }
    }

    /**
     * 检查视频是否存在
     */
    public void checkVideoExist(String videoId) {
        if (!videoRepository.isVideoExist(videoId)) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST, "视频不存在, videoId = " + videoId);
        }
    }

    /**
     * 校验创建视频请求参数
     */
    public void checkCreateVideoDTO(CreateVideoDTO createVideoDTO) {
        if (StringUtils.isEmpty(createVideoDTO.getRawFilename())) {
            throw new VideoException(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL,
                    "视频创建参数，原始文件名rawFilename为空");
        }
        if (StringUtils.isEmpty(createVideoDTO.getVideoType())) {
            throw new VideoException(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL,
                    "视频创建参数，视频类型videoType为空");
        }
        if (createVideoDTO.getSize() == null || createVideoDTO.getSize() == 0) {
            throw new VideoException(ErrorCode.VIDEO_CREATE_ARG_ILLEGAL,
                    "视频创建参数，原始文件大小size为空");
        }
    }

    /**
     * 检查watchId是否存在
     */
    public void checkWatchIdExist(String watchId) {
        if (!videoRepository.isWatchIdExist(watchId)) {
            throw new VideoException(ErrorCode.VIDEO_NOT_EXIST, "视频watchId不存在, watchId = " + watchId);
        }
    }

    /**
     * 检查视频是已就绪状态
     */
    public void checkVideoIsReady(Video video) {
        if (VideoStatus.isNotReady(video.getStatus())) {
            throw new VideoException(ErrorCode.VIDEO_NOT_READY,
                    "视频未就绪, video = " + JSON.toJSONString(video));
        }
    }

    /**
     * 检查视频是未就绪状态
     */
    public void checkVideoIsNotReady(Video video) {
        if (VideoStatus.isReady(video.getStatus())) {
            throw new VideoException(ErrorCode.VIDEO_IS_READY,
                    "视频已就绪, video = " + JSON.toJSONString(video));
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
            throw new VideoException(ErrorCode.VIDEO_AND_UPLOADER_NOT_MATCH, "视频不属于user, " +
                    "videoId = " + videoId + ", ownerId = " + ownerId + ", userId = " + userId);
        }
    }

    /**
     * 检查播放列表是否存在
     */
    public void checkPlaylistExist(String playlistId) {
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        if (playlist == null) {
            throw new VideoException(ErrorCode.PLAYLIST_NOT_EXIST,
                    "播放列表不存在, playlistId = " + playlistId);
        } else if (playlist.getIsDelete()) {
            throw new VideoException(ErrorCode.PLAYLIST_DELETED,
                    "播放列表已删除, playlistId = " + playlistId);
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
            throw new VideoException(ErrorCode.PLAYLIST_AND_USER_NOT_MATCH,
                    "playlist不属于user, playlistId = " + playlistId
                            + ", ownerId = " + ownerId + ", userId = " + userId);
        }
    }

    public void checkPlaylistCanDelete(String playlistId, String userId) {
        checkUserExist(userId);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        if (playlist == null) {
            throw new VideoException(ErrorCode.PLAYLIST_NOT_EXIST,
                    "播放列表不存在, playlistId = " + playlistId);
        }
        String ownerId = playlist.getOwnerId();
        if (!ownerId.equals(userId)) {
            throw new VideoException(ErrorCode.PLAYLIST_AND_USER_NOT_MATCH,
                    "playlist不属于user, playlistId = " + playlistId
                            + ", ownerId = " + ownerId + ", userId = " + userId);
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
    public void checkVideoVisibility(String visibility) {
        if (!StringUtils.equalsAny(visibility, Visibility.ALL)) {
            throw new VideoException("visibility不合法, visibility = " + visibility);
        }
    }

    /**
     * 检查addMode是否合法
     */
    public void checkPlaylistAddMode(String addMode) {
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
    public void checkPlaylistItemMoveMode(String moveMode) {
        if (!StringUtils.equalsAny(moveMode, MoveMode.ALL)) {
            throw new VideoException("mode不合法, mode = " + moveMode);
        }
    }

    /**
     * 检查文件是否存在
     */
    public void checkFileExist(String fileId) {
        if (!fileRepository.isFileExist(fileId)) {
            throw new VideoException(ErrorCode.FILE_NOT_EXIST, "文件不存在, fileId = " + fileId);
        }
    }

    /**
     * 检查文件是否就绪
     */
    public void checkFileIsReady(File file) {
        if (!FileStatus.READY.equals(file.getFileStatus())) {
            throw new VideoException(ErrorCode.FILE_NOT_READY, "文件未就绪, fileId = " + file.getId());
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
            throw new VideoException(ErrorCode.FILE_AND_USER_NOT_MATCH,
                    "文件和用户不匹配, user = " + userHolderUserId + ", fileId = " + fileId);
        }
    }
}
