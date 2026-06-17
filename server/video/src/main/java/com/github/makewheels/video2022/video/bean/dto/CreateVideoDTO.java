package com.github.makewheels.video2022.video.bean.dto;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.Data;

@Data
public class CreateVideoDTO {
    // 请求参数
    private String videoType;
    private String rawFilename;
    private String youtubeUrl;
    private Long size;
    private String ttl; // Time To Live 有效期
    private String transcodeMode; // 转码方式：AUTO（默认，云端 MPS/云函数）/ LOCAL（客户端本地 FFmpeg 转码后回传）

    // 后端service传递的参数
    private User user;
    private Video video;
    private File rawFile;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
