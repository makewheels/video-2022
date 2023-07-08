package com.github.makewheels.video2022.playlist.list.request;

import lombok.Data;

/**
 * 创建播放列表请求
 */
@Data
public class CreatePlaylistRequest {
    private String title;
    private String description;
}
