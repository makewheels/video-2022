package com.github.makewheels.video2022.video.bean.dto;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.File;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.video.Video;
import lombok.Data;

@Data
public class CreateVideoDTO {
    // 请求参数
    private String videoType;
    private String originalFilename;
    private String youtubeUrl;
    private Long size;

    // 后端service传递的参数
    private User user;
    private Video video;
    private File videoFile;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
