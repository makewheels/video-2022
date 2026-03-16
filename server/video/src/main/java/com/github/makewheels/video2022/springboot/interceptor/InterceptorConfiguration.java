package com.github.makewheels.video2022.springboot.interceptor;

import com.github.makewheels.video2022.openapi.oauth.OAuthInterceptor;
import com.github.makewheels.video2022.springboot.interceptor.admin.AdminApiKeyInterceptor;
import com.github.makewheels.video2022.springboot.interceptor.token.CheckTokenInterceptor;
import com.github.makewheels.video2022.springboot.interceptor.token.PutTokenInterceptor;
import com.github.makewheels.video2022.springboot.interceptor.requestlog.RequestLogInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfiguration implements WebMvcConfigurer {

    @Bean
    public AdminApiKeyInterceptor getAdminApiKeyInterceptor() {
        return new AdminApiKeyInterceptor();
    }

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

    @Bean
    public OAuthInterceptor getOAuthInterceptor() {
        return new OAuthInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getAdminApiKeyInterceptor())
                .addPathPatterns("/app/publishVersion");

        registry.addInterceptor(getPutTokenInterceptor())
                .addPathPatterns("/**");

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
                .addPathPatterns("/video/getVideoStatus")
                .addPathPatterns("/video/delete")
                .addPathPatterns("/videoLike/like")
                .addPathPatterns("/videoLike/dislike")
                .addPathPatterns("/comment/add")
                .addPathPatterns("/comment/delete")
                .addPathPatterns("/comment/like")
                .addPathPatterns("/user/updateProfile")
                .addPathPatterns("/user/getMyProfile")
                .addPathPatterns("/subscription/subscribe")
                .addPathPatterns("/subscription/unsubscribe")
                .addPathPatterns("/subscription/getStatus")
                .addPathPatterns("/subscription/getMySubscriptions")
        ;

        registry.addInterceptor(getRequestLogInterceptor())
                .addPathPatterns("/**");

        registry.addInterceptor(getOAuthInterceptor())
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/openapi/**");
    }
}
