package com.github.makewheels.video2022.interceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Bean
    public CheckTokenInterceptor getLoginInterceptor() {
        return new CheckTokenInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getLoginInterceptor())
                .excludePathPatterns("/**")
                .addPathPatterns("/upload.html")
                .addPathPatterns("/playlist/getMyPlaylistByPage")
        ;
    }
}
