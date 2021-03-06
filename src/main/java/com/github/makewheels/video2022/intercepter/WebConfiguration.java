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
        String[] swaggerExcludePatterns = new String[]{
                "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/api",
                "/api-docs", "/api-docs/**", "/v2/api-docs", "/v2/api-docs/**", "/doc.html**",
                "/error", "/favicon.ico"};
        registry.addInterceptor(getLoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(swaggerExcludePatterns)
                .excludePathPatterns("/healthCheck")
                .excludePathPatterns("/transcode/baiduTranscodeCallback")
                .excludePathPatterns("/video/getWatchInfo")
                .excludePathPatterns("/video/getVideoDetail")
                .excludePathPatterns("/video/getVideoListByUserId")
                .excludePathPatterns("/watch")
                .excludePathPatterns("/video/addWatchLog")
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
