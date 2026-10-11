package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import org.springframework.stereotype.Service;

/**
 * 自建 GPU 云函数转码实现：NVENC 重编码（H.264 SDR / HEVC Main10 HDR），协议 v1。
 */
@Service
public class AliyunCfGPUTranscodeImpl extends AbstractSelfHostedTranscodeImpl {
    @Override
    protected String provider() {
        return TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU;
    }
}
