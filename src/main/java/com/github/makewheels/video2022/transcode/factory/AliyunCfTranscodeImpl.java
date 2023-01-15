package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.bean.Transcode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 阿里云CPU云函数转码实现类
 */
@Service
@Slf4j
public class AliyunCfTranscodeImpl implements TranscodeService {
    @Override
    public void transcode(Transcode transcode) {

    }

    @Override
    public void handleCallback(Transcode transcode) {

    }
}
