package com.github.makewheels.video2022.playlist.item;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.check.CheckService;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemService;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemService;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemService;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.user.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 播放列表item服务
 */
@Service
@Slf4j
public class PlayItemService {
    @Resource
    private CheckService checkService;
    @Resource
    private PlaylistRepository playlistRepository;

    @Resource
    private AddPlayItemService addPlayItemService;
    @Resource
    private DeletePlayItemService deletePlayItemService;
    @Resource
    private MovePlayItemService movePlayItemService;

    /**
     * 把视频添加到播放列表
     */
    public void addVideoToPlaylist(AddPlayItemRequest addPlayItemRequest) {
        String playlistId = addPlayItemRequest.getPlaylistId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, UserHolder.getUserId());
        checkService.checkVideoNotBelongToPlaylist(playlistId, addPlayItemRequest.getVideoIdList());
        checkService.checkAddMode(addPlayItemRequest.getAddMode());

        // 执行添加
        addPlayItemService.addVideoToPlaylist(addPlayItemRequest);
    }

    /**
     * 把视频从播放列表移除
     */
    public void deletePlayItem(DeletePlayItemRequest deletePlayItemRequest) {
        // 通用校验
        String userId = UserHolder.getUserId();
        String playlistId = deletePlayItemRequest.getPlaylistId();
        checkService.checkPlaylistOwner(playlistId, userId);
        String deleteMode = deletePlayItemRequest.getDeleteMode();
        checkService.checkDeletePlayItemMode(deleteMode);

        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        log.info("给Playlist删除item, 删除前playlist = {}", JSON.toJSONString(playlist));

        // 执行删除items
        deletePlayItemService.deletePlayItem(playlist, deletePlayItemRequest);

        // 更新Playlist
        playlistRepository.save(playlist);
        log.info("给Playlist删除item, 删除后playlist = {}", JSON.toJSONString(playlist));
    }

    /**
     * 移动视频在播放列表中的位置
     */
    public void movePlayItem(MovePlayItemRequest movePlayItemRequest) {
        String playlistId = movePlayItemRequest.getPlaylistId();
        String moveMode = movePlayItemRequest.getMoveMode();

        // 校验
        checkService.checkPlaylistOwner(playlistId, UserHolder.getUserId());
        checkService.checkVideoBelongToPlaylist(playlistId, movePlayItemRequest.getVideoId());
        checkService.checkMoveMode(moveMode);

        // 执行移动
        movePlayItemService.movePlayItem(movePlayItemRequest);
    }

}
