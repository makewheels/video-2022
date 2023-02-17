package com.github.makewheels.video2022.video.bean.dto;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.video.Video;
import lombok.Data;

@Data
public class CreateVideoDTO {
    private String videoType;
    private String originalFilename;
    private String youtubeUrl;
    private Long size;

    private User user;
    private Video video;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}