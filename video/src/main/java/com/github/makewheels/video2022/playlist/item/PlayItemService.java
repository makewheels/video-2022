package com.github.makewheels.video2022.playlist.item;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.check.CheckService;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.add.AddPlayItemService;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.delete.DeletePlayItemService;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemRequest;
import com.github.makewheels.video2022.playlist.item.request.move.MovePlayItemService;
import com.github.makewheels.video2022.playlist.list.bean.IdBean;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private VideoRepository videoRepository;

    @Resource
    private AddPlayItemService addPlayItemService;
    @Resource
    private DeletePlayItemService deletePlayItemService;
    @Resource
    private MovePlayItemService movePlayItemService;

    /**
     * 把视频添加到播放列表
     */
    public Playlist addVideoToPlaylist(AddPlayItemRequest addPlayItemRequest) {
        String playlistId = addPlayItemRequest.getPlaylistId();

        // 校验
        checkService.checkPlaylistOwner(playlistId, UserHolder.getUserId());
        checkService.checkVideoNotBelongToPlaylist(playlistId, addPlayItemRequest.getVideoIdList());
        checkService.checkPlaylistAddMode(addPlayItemRequest.getAddMode());

        // 执行添加
        addPlayItemService.addVideoToPlaylist(addPlayItemRequest);
        return playlistRepository.getPlaylist(playlistId);
    }

    /**
     * 把视频从播放列表移除
     */
    public void deletePlayItem(DeletePlayItemRequest deletePlayItemRequest) {
        // 通用校验
        String userId = UserHolder.getUserId();
        String playlistId = deletePlayItemRequest.getPlaylistId();

        checkService.checkPlaylistOwner(playlistId, userId);
        checkService.checkVideoBelongToPlaylist(playlistId, deletePlayItemRequest.getVideoIdList());
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
        checkService.checkPlaylistItemMoveMode(moveMode);

        // 执行移动
        log.info("移动视频在播放列表中的位置, movePlayItemRequest = {}",
                JSON.toJSONString(movePlayItemRequest));
        movePlayItemService.movePlayItem(movePlayItemRequest);
    }

    /**
     * 获取播放列表items播放详情
     */
    public List<PlayItemVO> getPlayItemListDetail(String playlistId) {
        checkService.checkPlaylistExist(playlistId);
        // 拿到播放列表
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        List<IdBean> playlistVideoList = playlist.getVideoList();
        // 拿到列表中所有对应视频
        List<String> videoIdList = playlistVideoList.stream()
                .map(IdBean::getVideoId).collect(Collectors.toList());
        Map<String, Video> videoMap = videoRepository.getMapByIdList(videoIdList);
        // 组装返回
        List<PlayItemVO> result = new ArrayList<>(playlistVideoList.size());
        for (IdBean idBean : playlistVideoList) {
            String videoId = idBean.getVideoId();
            Video video = videoMap.get(videoId);
            PlayItemVO playItemVO = new PlayItemVO();
            BeanUtils.copyProperties(video, playItemVO);
            playItemVO.setPlayItemId(idBean.getPlayItemId());
            playItemVO.setVideoId(videoId);
            playItemVO.setVideoCreateTime(DateUtil.formatDate(video.getCreateTime()));
            playItemVO.setVideoUpdateTime(DateUtil.formatDate(video.getUpdateTime()));
            playItemVO.setVideoStatus(video.getStatus());
            result.add(playItemVO);
        }
        return result;
    }
}
