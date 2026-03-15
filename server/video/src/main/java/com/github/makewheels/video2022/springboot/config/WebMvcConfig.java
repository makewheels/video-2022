package com.github.makewheels.video2022.springboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 配置静态资源处理，避免与API路由冲突
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只将 /static/** 映射到静态资源，其他路径不处理
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}