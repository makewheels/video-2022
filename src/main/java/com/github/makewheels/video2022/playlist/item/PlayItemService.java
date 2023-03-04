package com.github.makewheels.video2022.playlist.item;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.check.CheckService;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.playlist.item.request.add.AddMode;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemRequest;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;

/**
 * 播放列表项服务
 */
@Service
@Slf4j
public class PlayItemService {
    @Resource
    private CheckService checkService;
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
        playlistRepository.save(playlist);
        log.info("给Playlist添加item = {}", JSON.toJSONString(playlist));
    }

    /**
     * 把视频添加到播放列表
     */
    public void addVideoListToPlaylist(AddPlayItemRequest addPlayItemRequest) {
        String playlistId = addPlayItemRequest.getPlaylistId();
        List<String> videoIdList = addPlayItemRequest.getVideoIdList();
        String addMode = addPlayItemRequest.getAddMode();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoNotBelongToPlaylist(playlistId, videoIdList);
        checkService.checkAddMode(addMode);

        // 遍历添加每一个item
        for (String videoId : videoIdList) {
            addSingleVideoToPlaylist(playlistId, videoId, userId, addMode);
        }
    }

    /**
     * 把视频从播放列表移除
     */
    public void deletePlaylistItem(DeletePlayItemRequest deletePlayItemRequest) {
        // 通用校验
        String userId = UserHolder.getUserId();
        String playlistId = deletePlayItemRequest.getPlaylistId();
        checkService.checkPlaylistOwner(playlistId, userId);
        String deleteMode = deletePlayItemRequest.getDeleteMode();
        checkService.checkDeletePlayItemMode(deleteMode);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);

        // 更新Playlist
        log.info("给Playlist删除item = {}", JSON.toJSONString(playlist));
        playlistRepository.save(playlist);
    }

    /**
     * 移动视频位置，根据mode的五种模式，移动
     * TO_INDEX            移到指定索引位置
     * BEFORE_VIDEO     移到指定视频之前
     * AFTER_VIDEO      移到指定视频之后
     * TO_TOP             移到播放列表最前面
     * TO_BOTTOM        移到播放列表最后面
     */
    public void movePlaylistItem(MovePlayItemRequest movePlayItemRequest) {
        String playlistId = movePlayItemRequest.getPlaylistId();
        String videoId = movePlayItemRequest.getVideoId();
        String mode = movePlayItemRequest.getMoveMode();
        String userId = UserHolder.getUserId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoBelongToPlaylist(playlistId, videoId);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        List<IdBean> idBeanList = playlist.getIdBeanList();
        if (idBeanList == null) {
            idBeanList = new LinkedList<>();
            playlist.setIdBeanList(idBeanList);
        }
    }

}
