package com.github.makewheels.video2022.playlist.dto;

import lombok.Data;

@Data
public class MoveVideoDTO {
    private String playlistId;
    private String videoId;

    //TO_INDEX            移到指定索引位置
    //BEFORE_VIDEO     移到指定视频之前
    //AFTER_VIDEO      移到指定视频之后
    //TO_TOP             移到播放列表最前面
    //TO_BOTTOM        移到播放列表最后面
    private String mode;

    private Integer toIndex;
    private String toVideoId;
}
