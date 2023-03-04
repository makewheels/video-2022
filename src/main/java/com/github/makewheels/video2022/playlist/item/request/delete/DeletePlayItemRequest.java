package com.github.makewheels.video2022.playlist.item.request.delete;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 把视频从播放列表中移除请求
 */
@Data
public class DeletePlayItemRequest {
    private String playlistId;
    private String deleteMode;

    private List<String> videoIdList;
    private List<String> playlistItemIdList;
    private List<Integer> indexList;
    private Integer startIndex;
    private Integer endIndex;
    private Date startTime;
    private Date endTime;

}
