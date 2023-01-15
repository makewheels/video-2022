package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.Transcode;
import org.springframework.stereotype.Service;

/**
 * 转码接口
 */
@Service
public interface TranscodeService {
    void transcode(Transcode transcode);
    void handleCallback(Transcode transcode);
}
