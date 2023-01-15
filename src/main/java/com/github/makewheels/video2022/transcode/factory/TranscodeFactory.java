package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.TranscodeProvider;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工程模式分配 Service
 * <a href="https://www.cnblogs.com/ciel717/p/16190762.html">SpringBoot使用设计模式一策略模式</a>
 */
@Service
public class TranscodeFactory {
    @Resource
    private Map<String, TranscodeService> map = new ConcurrentHashMap<>();

    public TranscodeService getTranscodeService(String provider) {
        if (provider.equals(TranscodeProvider.ALIYUN_MPS)) {
            return map.get(AliyunMpsTranscodeImpl.class.getName());
        }
        throw new RuntimeException("未找到实现类，provider = " + provider);
    }
}
