package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.bean.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

/**
 * 媒体信息
 */
@Data
public class MediaInfo {
    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Integer bitrate;
    private Long duration;      //视频时长，单位毫秒

    private JSONObject response;
}
