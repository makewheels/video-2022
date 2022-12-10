package com.github.makewheels.video2022.etc.interceptor;

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
                .excludePathPatterns("/app/checkUpdate")
                .excludePathPatterns("/favicon.ico")
                .excludePathPatterns("/static/favicon.ico")
                .excludePathPatterns("/healthCheck")
                .excludePathPatterns("/error")

                .excludePathPatterns("/login.html")
                .excludePathPatterns("/user/requestVerificationCode")
                .excludePathPatterns("/user/submitVerificationCode")
                .excludePathPatterns("/user/getUserByToken")
                .excludePathPatterns("/client/requestClientId")
                .excludePathPatterns("/session/requestSessionId")

                .excludePathPatterns("/upload.html")
                .excludePathPatterns("/save-token.html")
                .excludePathPatterns("/transfer-youtube.html")

                .excludePathPatterns("/transcode/baiduTranscodeCallback")
                .excludePathPatterns("/transcode/aliyunCloudFunctionTranscodeCallback")

                .excludePathPatterns("/watch")
                .excludePathPatterns("/watchController/addWatchLog")
                .excludePathPatterns("/watchController/getWatchInfo")
                .excludePathPatterns("/watchController/getM3u8Content.m3u8")
                .excludePathPatterns("/watchController/getMultivariantPlaylist")

                .excludePathPatterns("/video/getVideoDetail")
                .excludePathPatterns("/video/getVideoListByUserId")
                .excludePathPatterns("/file/access")
                .excludePathPatterns("/statistics/getTrafficConsume")
        ;
    }
}
