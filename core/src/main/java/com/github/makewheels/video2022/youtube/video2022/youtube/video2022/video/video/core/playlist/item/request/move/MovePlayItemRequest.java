package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.playlist.item.request.move;

import lombok.Data;

/**
 * 移动视频在播放列表中的位置请求
 */
@Data
public class MovePlayItemRequest {
    private String playlistId;
    private String videoId;
    private String moveMode;
    private Integer toIndex;
    private String toVideoId;
}
