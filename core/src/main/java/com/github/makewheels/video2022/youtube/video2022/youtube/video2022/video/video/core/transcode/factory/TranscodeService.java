package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.transcode.factory;

import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.videopackage.bean.entity.Video;
import org.springframework.stereotype.Service;

/**
 * 转码接口
 */
@Service
public interface TranscodeService {
    void transcode(Video video, Transcode transcode);

    void callback(String jobId);
}
