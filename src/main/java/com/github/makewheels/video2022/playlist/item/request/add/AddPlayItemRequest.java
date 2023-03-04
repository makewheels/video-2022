package com.github.makewheels.video2022.playlist.item.request.add;

import lombok.Data;

import java.util.List;

/**
 * 添加视频到播放列表请求
 */
@Data
public class AddPlayItemRequest {
    private String playlistId;
    private List<String> videoIdList;
    private String addMode;
}
