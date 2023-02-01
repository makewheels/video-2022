package com.github.makewheels.video2022.video.bean;

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


    private JSONObject mediaInfo;
}
