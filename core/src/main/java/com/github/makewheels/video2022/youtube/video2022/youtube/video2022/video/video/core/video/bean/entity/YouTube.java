package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.bean.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 视频YouTube信息
 */
@Data
public class YouTube {
    @Indexed
    private String videoId;
    private String url;
    private JSONObject videoInfo;
    private Date publishTime;
}
