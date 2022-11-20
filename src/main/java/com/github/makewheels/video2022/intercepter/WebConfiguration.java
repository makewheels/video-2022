package com.github.makewheels.video2022.intercepter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Bean
    public LoginInterceptor getLoginInterceptor() {
        return new LoginInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getLoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/healthCheck")
                .excludePathPatterns("/transcode/baiduTranscodeCallback")
                .excludePathPatterns("/video/getWatchInfo")
                .excludePathPatterns("/video/getVideoDetail")
                .excludePathPatterns("/video/getVideoListByUserId")
                .excludePathPatterns("/video/addWatchLog")
                .excludePathPatterns("/video/getM3u8Content.m3u8")
                .excludePathPatterns("/file/access")
                .excludePathPatterns("/watch")
                .excludePathPatterns("/save-token.html")
                .excludePathPatterns("/cdn/onSoftRoutePrefetchFinish")
                .excludePathPatterns("/upload.html")
                .excludePathPatterns("/transfer-youtube.html")
                .excludePathPatterns("/transcode/aliyunCloudFunctionTranscodeCallback")
                .excludePathPatterns("/upload-aliyun.html")
                .excludePathPatterns("/app/checkUpdate")
                .excludePathPatterns("/favicon.ico")
        ;
    }
}
