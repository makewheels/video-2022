package com.github.makewheels.video2022.playlist.list.request;

import lombok.Data;

/**
 * 更新播放列表请求
 */
@Data
public class UpdatePlaylistRequest {
    private String playlistId;
    private String title;
    private String description;
    private String visibility;
}
