package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.Video;
import org.springframework.stereotype.Service;

/**
 * 转码接口
 */
@Service
public interface TranscodeService {
    Transcode transcode(Video video, Transcode transcode);

    void callback(String jobId);
}
