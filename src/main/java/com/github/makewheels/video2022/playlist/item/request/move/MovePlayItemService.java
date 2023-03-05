package com.github.makewheels.video2022.playlist.item.request.move;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.exception.VideoException;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 移动视频在播放列表中的位置
 */
@Service
@Slf4j
public class MovePlayItemService {
    @Resource
    private PlaylistRepository playlistRepository;

    /**
     * 根据模式找到目标playItem
     */
    private PlayItem findTargetPlayItem(
            MovePlayItemRequest movePlayItemRequest, List<PlayItem> playItemList) {
        String moveMode = movePlayItemRequest.getMoveMode();
        switch (moveMode) {
            case MoveMode.TO_INDEX:
                Integer toIndex = movePlayItemRequest.getToIndex();
                return playItemList.get(toIndex);
            case MoveMode.BEFORE_VIDEO: {
                String toVideoId = movePlayItemRequest.getToVideoId();
                return playItemList.stream()
                        .filter(item -> item.getVideoId().equals(toVideoId))
                        .findFirst().orElse(null);
            }
            case MoveMode.AFTER_VIDEO: {
                String toVideoId = movePlayItemRequest.getToVideoId();
                return playItemList.stream()
                        .filter(item -> item.getVideoId().equals(toVideoId))
                        .findFirst().orElse(null);
            }
            case MoveMode.TO_TOP:
                return playItemList.get(0);
            case MoveMode.TO_BOTTOM:
                return playItemList.get(playItemList.size() - 1);
        }
        throw new VideoException("未知的移动模式, moveMode: " + moveMode);
    }

    /**
     * 移动playItem
     */
    public void movePlayItem(MovePlayItemRequest movePlayItemRequest) {
        String playlistId = movePlayItemRequest.getPlaylistId();
        String videoId = movePlayItemRequest.getVideoId();

        // 查playlist
        Playlist playlist = playlistRepository.getPlaylist(playlistId);
        List<PlayItem> playItemList = playlistRepository.getPlayItemList(playlist);

        // 找到要移动的playItem
        PlayItem sourcePlayItem = playItemList.stream()
                .filter(item -> item.getVideoId().equals(videoId))
                .findFirst().orElse(null);

        // 根据模式找到targetPlayItem
        PlayItem targetPlayItem = findTargetPlayItem(movePlayItemRequest, playItemList);
        // 执行交换
        log.info("移动之前playItemList: {}", JSON.toJSONString(playItemList));
        Collections.swap(playItemList,
                playItemList.indexOf(sourcePlayItem),
                playItemList.indexOf(targetPlayItem));
        log.info("移动之后playItemList: {}", JSON.toJSONString(playItemList));

        // 保存到数据库
        playlist.setUpdateTime(new Date());
        playlistRepository.save(playlist);


    }

}
