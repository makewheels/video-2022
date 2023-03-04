package com.github.makewheels.video2022.playlist.item.request.delete;

/**
 * 删除播放列表中的视频的模式
 */
public interface DeleteMode {
    String VIDEO_ID_LIST = "VIDEO_ID_LIST";  // 根据指定videoId删除
    String PLAY_ITEM_ID_LIST = "PLAY_ITEM_ID_LIST";    // 根据指定playItemId删除

    String SINGLE_INDEX = "SINGLE_INDEX";    // 根据指定索引删除
    String INDEX_LIST = "INDEX_LIST";        // 根据指定索引列表删除
    String INDEX_RANGE = "INDEX_RANGE";      // 根据索引范围删除

    String CREATE_TIME_RANGE = "TIME_RANGE";        // 根据创建时间范围删除
    String UPDATE_TIME_RANGE = "UPDATE_TIME_RANGE"; // 根据更新时间范围删除

    String ALL_ITEMS = "ALL_ITEMS";          // 删除全部items

    String[] ALL = {VIDEO_ID_LIST, PLAY_ITEM_ID_LIST, SINGLE_INDEX, INDEX_LIST, INDEX_RANGE,
            CREATE_TIME_RANGE, UPDATE_TIME_RANGE, ALL_ITEMS};

}
