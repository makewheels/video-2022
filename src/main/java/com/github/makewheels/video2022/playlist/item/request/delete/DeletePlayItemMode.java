package com.github.makewheels.video2022.playlist.item.request.delete;

/**
 * 删除播放列表中的视频的模式
 */
public interface DeletePlayItemMode {
    String VIDEO_ID_LIST = "VIDEO_ID_LIST";       // 根据指定videoId删除
    String PLAYLIST_ITEM_ID = "PLAYLIST_ITEM_ID"; // 根据指定playlistItemId删除
    String INDEX = "INDEX";                       // 根据指定索引删除
    String INDEX_RANGE = "INDEX_RANGE";           // 根据索引范围删除
    String TIME_RANGE = "TIME_RANGE";             // 根据时间范围删除
    String ALL_ITEMS = "ALL_ITEMS";               // 删除全部items

    String[] ALL = {VIDEO_ID_LIST, PLAYLIST_ITEM_ID, INDEX, INDEX_RANGE, TIME_RANGE, ALL_ITEMS};

}
