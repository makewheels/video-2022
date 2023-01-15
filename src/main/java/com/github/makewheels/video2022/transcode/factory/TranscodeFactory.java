package com.github.makewheels.video2022.transcode.factory;

import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.beans.Introspector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工厂模式分配 Service
 * <a href="https://www.cnblogs.com/ciel717/p/16190762.html">SpringBoot使用设计模式一策略模式</a>
 */
@Service
public class TranscodeFactory {

    //让spring把所有实现类都注入在这里，比如：key = aliyunMpsTranscodeImpl
    @Resource
    private Map<String, TranscodeService> map = new ConcurrentHashMap<>();

    /**
     * 获取class名，用于注入的map，首字母小写
     */
    private String getClassName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Introspector.decapitalize(simpleName);
    }

    /**
     * 获取具体实现类
     */
    public TranscodeService getService(String provider) {
        switch (provider) {
            case TranscodeProvider.ALIYUN_MPS:
                return map.get(getClassName(AliyunMpsTranscodeImpl.class));
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return map.get(getClassName(AliyunCfTranscodeImpl.class));
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU:
                return map.get(getClassName(AliyunCfGPUTranscodeImpl.class));
        }
        throw new RuntimeException("未找到实现类，provider = " + provider);
    }

}
