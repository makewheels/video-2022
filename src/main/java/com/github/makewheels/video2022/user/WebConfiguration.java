package com.github.makewheels.video2022.user;

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
                .excludePathPatterns("/save-token.html")
                .excludePathPatterns("/upload.html")
                .excludePathPatterns("/transfer-youtube.html")
                .excludePathPatterns("/upload-aliyun.html")
                .excludePathPatterns("/app/checkUpdate")
                .excludePathPatterns("/favicon.ico")
                .excludePathPatterns("/healthCheck")

                .excludePathPatterns("/transcode/baiduTranscodeCallback")
                .excludePathPatterns("/transcode/aliyunCloudFunctionTranscodeCallback")

                .excludePathPatterns("/video/getWatchInfo")
                .excludePathPatterns("/video/getVideoDetail")
                .excludePathPatterns("/video/getVideoListByUserId")
                .excludePathPatterns("/video/addWatchLog")
                .excludePathPatterns("/video/getM3u8Content.m3u8")
                .excludePathPatterns("/file/access")
                .excludePathPatterns("/watch")
                .excludePathPatterns("/statistics/getTrafficConsume")
        ;
    }
}
