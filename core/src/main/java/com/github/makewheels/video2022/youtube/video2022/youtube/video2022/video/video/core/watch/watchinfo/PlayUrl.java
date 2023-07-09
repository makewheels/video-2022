package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.watch.watchinfo;

import com.alibaba.fastjson.JSON;
import lombok.Data;

@Data
public class PlayUrl {
    private String resolution;
    private String url;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
