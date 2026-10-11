package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import org.springframework.stereotype.Service;

/**
 * 自建 CPU 云函数转码实现：无需重编码的 remux（H.264/AAC 合规源直切 HLS），协议 v1。
 */
@Service
public class AliyunCfCpuTranscodeImpl extends AbstractSelfHostedTranscodeImpl {
    @Override
    protected String provider() {
        return TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU;
    }
}
