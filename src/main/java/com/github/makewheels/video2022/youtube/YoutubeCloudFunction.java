package com.github.makewheels.video2022.youtube;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 阿里云云函数：搬运YouTube视频
 */
@Slf4j
public class YoutubeCloudFunction implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context)
            throws IOException {
        // 调用Google接口
        // 使用yt-dlp下载
        // 上传到北京OSS

    }

}
