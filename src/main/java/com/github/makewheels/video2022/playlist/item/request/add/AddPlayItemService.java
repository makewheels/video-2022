package com.github.makewheels.video2022.playlist.item.request.add;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * 添加视频到播放列表
 */
@Service
@Slf4j
public class AddPlayItemService {
    @Resource
    private PlaylistRepository playlistRepository;

    /**
     * 把单个视频添加到播放列表
     */
    private void addSingleVideoToPlaylist(
            String playlistId, String videoId, String userId, String addMode) {
        // 新建playlistItem
        PlayItem playItem = new PlayItem();
        playItem.setPlaylistId(playlistId);
        playItem.setVideoId(videoId);
        playItem.setOwner(userId);
        playlistRepository.save(playItem);
        log.info("新建PlaylistItem = {}", JSON.toJSONString(playItem));

        // 给playlist添加item
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        List<IdBean> idBeanList = playlist.getIdBeanList();
        if (idBeanList == null) {
            idBeanList = new LinkedList<>();
            playlist.setIdBeanList(idBeanList);
        }

        // 根据模式添加item
        IdBean idBean = new IdBean();
        idBean.setPlaylistItemId(playItem.getId());
        idBean.setVideoId(videoId);
        if (AddMode.ADD_TO_TOP.equals(addMode)) {
            idBeanList.add(0, idBean);
        } else if (AddMode.ADD_TO_BOTTOM.equals(addMode)) {
            idBeanList.add(idBean);
        }

        // 更新Playlist
        playlist.setUpdateTime(new Date());
        playlistRepository.save(playlist);
        log.info("给Playlist添加item = {}", JSON.toJSONString(playlist));
    }

    /**
     * 把视频添加到播放列表
     */
    public void addVideoToPlaylist(AddPlayItemRequest addPlayItemRequest) {
        List<String> videoIdList = addPlayItemRequest.getVideoIdList();
        String playlistId = addPlayItemRequest.getPlaylistId();
        String addMode = addPlayItemRequest.getAddMode();
        for (String videoId : videoIdList) {
            String userId = UserHolder.getUserId();
            addSingleVideoToPlaylist(playlistId, videoId, userId, addMode);
        }
    }
}
