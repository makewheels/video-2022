package com.github.makewheels.video2022.playlist.list;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.check.CheckService;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.playlist.list.request.CreatePlaylistRequest;
import com.github.makewheels.video2022.playlist.list.request.UpdatePlaylistRequest;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 播放列表服务
 */
@Service
@Slf4j
public class PlaylistService {
    @Resource
    private PlaylistRepository playlistRepository;
    @Resource
    private CheckService checkService;

    /**
     * 创建播放列表
     */
    public Playlist createPlaylist(CreatePlaylistRequest createPlaylistRequest) {
        Playlist playlist = new Playlist();
        String title = createPlaylistRequest.getTitle();
        playlist.setTitle(title);
        playlist.setDescription(createPlaylistRequest.getDescription());
        playlist.setOwnerId(UserHolder.getUserId());
        playlistRepository.save(playlist);
        log.info("创建播放列表, title = {}, playlist = {}", title, JSON.toJSONString(playlist));
        return playlist;
    }

    /**
     * 更新播放列表
     */
    public Playlist updatePlaylist(UpdatePlaylistRequest updatePlaylistRequest) {
        String playlistId = updatePlaylistRequest.getPlaylistId();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        String visibility = updatePlaylistRequest.getVisibility();
        checkService.checkVisibility(visibility);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        log.info("准备更新播放列表, 更新前playlist = {}", JSON.toJSONString(playlist));
        playlist.setTitle(updatePlaylistRequest.getTitle());
        playlist.setDescription(updatePlaylistRequest.getDescription());
        playlist.setVisibility(visibility);
        playlist.setUpdateTime(new Date());
        playlistRepository.save(playlist);
        log.info("更新播放列表后, playlist = {}", JSON.toJSONString(playlist));
        return playlist;
    }

    /**
     * 删除播放列表
     */
    public void deletePlaylist(String playlistId) {
        String userId = UserHolder.getUserId();
        checkService.checkPlaylistOwner(playlistId, userId);
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        playlist.setIsDelete(true);
        playlistRepository.save(playlist);
        log.info("删除播放列表, playlist = {}", JSON.toJSONString(playlist));
    }

    /**
     * 恢复播放列表
     */
    public void recoverPlaylist(String playlistId) {
        String userId = UserHolder.getUserId();
        checkService.checkPlaylistOwner(playlistId, userId);
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        playlist.setIsDelete(false);
        playlistRepository.save(playlist);
        log.info("恢复播放列表, playlist = {}", JSON.toJSONString(playlist));
    }

    /**
     * 根据id获取播放列表
     */
    public Playlist getPlaylistById(String playlistId) {
        return playlistRepository.getPlaylist(playlistId);
    }

    /**
     * 分页获取播放列表
     */
    public List<Playlist> getPlaylistByPage(String userId, int skip, int limit) {
        return playlistRepository.getPlaylistByPage(userId, skip, limit);
    }

    /**
     * 获取视频所在的播放列表
     */
    public List<String> getPlaylistByVideoId(String videoId) {
        return playlistRepository.getPlaylistByVideoAndUser(videoId, UserHolder.getUserId());
    }
}
