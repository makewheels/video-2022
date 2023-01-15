package com.github.makewheels.video2022.transcode.strategy;

import com.github.makewheels.video2022.transcode.Transcode;
import org.springframework.stereotype.Service;

/**
 * 转码接口，使用策略模式
 */
@Service
public interface TranscodeStrategy {
    void handleCallback(Transcode transcode);
}
