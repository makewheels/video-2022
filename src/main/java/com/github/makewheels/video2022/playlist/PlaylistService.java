package com.github.makewheels.video2022.playlist;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.check.CheckService;
import com.github.makewheels.video2022.etc.exception.VideoException;
import com.github.makewheels.video2022.playlist.bean.Playlist;
import com.github.makewheels.video2022.playlist.bean.PlaylistItem;
import com.github.makewheels.video2022.playlist.dto.AddVideoToPlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.CreatePlaylistDTO;
import com.github.makewheels.video2022.playlist.dto.MoveVideoDTO;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;

@Service
@Slf4j
public class PlaylistService {
    @Resource
    private PlaylistRepository playlistRepository;
    @Resource
    private CacheService cacheService;
    @Resource
    private CheckService checkService;

    /**
     * 创建播放列表
     */
    public Playlist createPlaylist(CreatePlaylistDTO createPlaylistDTO) {
        Playlist playlist = new Playlist();
        playlist.setTitle(createPlaylistDTO.getTitle());
        playlist.setDescription(createPlaylistDTO.getDescription());
        playlist.setOwnerId(UserHolder.getUserId());
        playlistRepository.save(playlist);
        log.info("创建播放列表, playlist = {}", JSON.toJSONString(playlist));
        return playlist;
    }

    /**
     * 根据id获取播放列表
     */
    public Playlist getPlaylistById(String playlistId) {
        return cacheService.getPlaylist(playlistId);
    }

    /**
     * 把视频添加到播放列表
     */
    public void addVideoToPlaylist(AddVideoToPlaylistDTO addVideoToPlaylistDTO) {
        String playlistId = addVideoToPlaylistDTO.getPlaylistId();
        String videoId = addVideoToPlaylistDTO.getVideoId();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoNotBelongsToPlaylist(videoId, playlistId);

        // 添加item
        PlaylistItem playlistItem = new PlaylistItem();
        playlistItem.setPlaylistId(playlistId);
        playlistItem.setVideoId(videoId);
        playlistItem.setOwner(userId);
        cacheService.updatePlaylistItem(playlistItem);
        log.info("新增PlaylistItem = {}", JSON.toJSONString(playlistItem));

        Playlist playlist = cacheService.getPlaylist(playlistId);
        List<String> itemIdList = playlist.getItemIdList();
        if (itemIdList == null) {
            itemIdList = new LinkedList<>();
            playlist.setItemIdList(itemIdList);
        }
        // 默认把新的item放到最前面
        itemIdList.add(0, playlistItem.getId());

        // 更新Playlist
        log.info("给Playlist新增item = {}", JSON.toJSONString(playlist));
        cacheService.updatePlaylist(playlist);
    }

    /**
     * 移动视频位置
     */
    public void moveVideo(MoveVideoDTO moveVideoDTO) {
        String playlistId = moveVideoDTO.getPlaylistId();
        String videoId = moveVideoDTO.getVideoId();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoBelongsToPlaylist(videoId, playlistId);
        if (StringUtils.isEmpty(moveVideoDTO.getMode())) {
            throw new VideoException("移动模式不能为空, moveVideoDTO = " + JSON.toJSONString(moveVideoDTO));
        }

        Playlist playlist = cacheService.getPlaylist(playlistId);
        List<String> itemIdList = playlist.getItemIdList();
        if (itemIdList == null) {
            itemIdList = new LinkedList<>();
            playlist.setItemIdList(itemIdList);
        }
        // 找到videoId对应的item
        PlaylistItem playlistItem = null;
        for (String itemId : itemIdList) {
            PlaylistItem item = cacheService.getPlaylistItem(itemId);
            if (item.getVideoId().equals(videoId)) {
                playlistItem = item;
                break;
            }
        }
        if (playlistItem == null) {
            log.error("PlaylistItem不存在, playlistId = {}, videoId = {}", playlistId, videoId);
            throw new VideoException("PlaylistItem不存在, playlistId = " + playlistId + ", videoId = " + videoId);
        }

        // 移动item
        itemIdList.remove(playlistItem.getId());
        switch (moveVideoDTO.getMode()) {
            case MoveMode.MOVE_TO_POSITION:
                itemIdList.add(moveVideoDTO.getToPosition(), playlistItem.getId());
                break;
            case MoveMode.MOVE_BEFORE_VIDEO:
                int position = itemIdList.indexOf(moveVideoDTO.getToVideoId());
                itemIdList.add(position, playlistItem.getId());
                break;
            case MoveMode.MOVE_AFTER_VIDEO:
                position = itemIdList.indexOf(moveVideoDTO.getToVideoId());
                itemIdList.add(position + 1, playlistItem.getId());
                break;
            default:
                log.error("未知的移动模式, moveMode = {}", moveVideoDTO.getMode());
                throw new RuntimeException("未知的移动模式");
        }

        // 更新Playlist
        log.info("移动PlaylistItem = {}", JSON.toJSONString(playlist));
        cacheService.updatePlaylist(playlist);
    }
}
