package com.github.makewheels.video2022.playlist.item.request.delete;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.springboot.exception.VideoException;
import com.github.makewheels.video2022.playlist.PlaylistRepository;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 删除播放列表中的视频
 */
@Service
@Slf4j
public class DeletePlayItemService {
    @Resource
    private PlaylistRepository playlistRepository;

    /**
     * 找出要删除的播放列表items
     */
    private List<PlayItem> getDeletePlayItems(
            Playlist playlist, DeletePlayItemRequest deletePlayItemRequest) {
        List<PlayItem> playItemList = playlistRepository.getPlayItemList(playlist);

        // 根据模式找出要删除的items
        String deleteMode = deletePlayItemRequest.getDeleteMode();
        switch (deleteMode) {
            case DeleteMode.VIDEO_ID_LIST:
                return playItemList.stream()
                        .filter(playItem -> deletePlayItemRequest.getVideoIdList()
                                .contains(playItem.getVideoId()))
                        .collect(Collectors.toList());
            case DeleteMode.PLAY_ITEM_ID_LIST:
                return playItemList.stream()
                        .filter(playItem -> deletePlayItemRequest.getPlayItemIdList()
                                .contains(playItem.getId()))
                        .collect(Collectors.toList());
            case DeleteMode.SINGLE_INDEX:
                Integer singleIndex = deletePlayItemRequest.getSingleIndex();
                Lists.newArrayList(playItemList.get(singleIndex));
                break;
            case DeleteMode.INDEX_LIST:
                List<Integer> indexList = deletePlayItemRequest.getIndexList();
                List<PlayItem> result = new ArrayList<>(indexList.size());
                for (Integer index : indexList) {
                    result.add(playItemList.get(index));
                }
                return result;
            case DeleteMode.INDEX_RANGE:
                Integer startIndex = deletePlayItemRequest.getStartIndex();
                Integer endIndex = deletePlayItemRequest.getEndIndex();
                return playItemList.subList(startIndex, endIndex);
            case DeleteMode.CREATE_TIME_RANGE:
                return playItemList.stream()
                        .filter(playItem -> playItem.getCreateTime().getTime() >=
                                deletePlayItemRequest.getStartTime().getTime()
                                && playItem.getCreateTime().getTime() <=
                                deletePlayItemRequest.getEndTime().getTime())
                        .collect(Collectors.toList());
            case DeleteMode.UPDATE_TIME_RANGE:
                return playItemList.stream()
                        .filter(playItem -> playItem.getUpdateTime().getTime() >=
                                deletePlayItemRequest.getStartTime().getTime()
                                && playItem.getUpdateTime().getTime() <=
                                deletePlayItemRequest.getEndTime().getTime())
                        .collect(Collectors.toList());
            case DeleteMode.ALL_ITEMS:
                return playItemList;
        }
        throw new VideoException("未知的删除模式, deleteMode = " + deleteMode);
    }

    /**
     * 执行删除播放列表item
     */
    public void deletePlayItem(Playlist playlist, DeletePlayItemRequest deletePlayItemRequest) {
        // 找出要删除的items
        List<PlayItem> playItemList = getDeletePlayItems(playlist, deletePlayItemRequest);
        // 遍历逐个删除PlayItem
        for (PlayItem playItem : playItemList) {
            playItem.setIsDelete(true);
            playItem.setUpdateTime(new Date());
            playlistRepository.save(playItem);
            log.info("删除PlaylistItem = {}", JSON.toJSONString(playItem));
        }

        // 从playlist中删除items
        List<String> playItemIdList = playItemList.stream()
                .map(PlayItem::getId).collect(Collectors.toList());
        log.info("删除的playItemIdList = {}", JSON.toJSONString(playItemIdList));
        playlist.getVideoList().removeIf(idBean -> playItemIdList.contains(idBean.getPlayItemId()));
    }

}
