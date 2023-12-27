package com.github.makewheels.video2022.etc.springboot.interceptor;

import com.github.makewheels.video2022.etc.springboot.interceptor.token.CheckTokenInterceptor;
import com.github.makewheels.video2022.etc.springboot.interceptor.token.PutTokenInterceptor;
import com.github.makewheels.video2022.etc.springboot.interceptor.requestlog.RequestLogInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfiguration implements WebMvcConfigurer {

    @Bean
    public PutTokenInterceptor getPutTokenInterceptor() {
        return new PutTokenInterceptor();
    }

    @Bean
    public CheckTokenInterceptor getCheckTokenInterceptor() {
        return new CheckTokenInterceptor();
    }

    @Bean
    public RequestLogInterceptor getRequestLogInterceptor() {
        return new RequestLogInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 放token
        registry.addInterceptor(getPutTokenInterceptor())
                .addPathPatterns("/**");

        // 校验登录状态
        registry.addInterceptor(getCheckTokenInterceptor())
                .addPathPatterns("/save-token.html")
                .addPathPatterns("/playlist/getMyPlaylistByPage")
                .addPathPatterns("/video/create")
                .addPathPatterns("/file/getUploadCredentials")
                .addPathPatterns("/video/rawFileUploadFinish")
                .addPathPatterns("/file/uploadFinish")
                .addPathPatterns("/video/updateInfo")
                .addPathPatterns("/playlist/addPlaylistItem")
                .addPathPatterns("/video/getMyVideoList")
        ;

        // 记录请求日志
        registry.addInterceptor(getRequestLogInterceptor())
                .addPathPatterns("/**");
    }
}
