package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.springframework.stereotype.Service;

/**
 * 转码接口
 */
@Service
public interface TranscodeService {
    void transcode(Video video, Transcode transcode);

    default com.github.makewheels.video2022.transcode.task.FcTask prepare(Video video, Transcode transcode) {
        throw new UnsupportedOperationException("该 provider 不使用自建函数任务");
    }

    void callback(String jobId);
}
