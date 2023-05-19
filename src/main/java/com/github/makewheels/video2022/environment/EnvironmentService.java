package com.github.makewheels.video2022.environment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentService {
    @Value("${internal-base-url}")
    private String internalBaseUrl;
    @Value("${external-base-url}")
    private String externalBaseUrl;

    @Value("${short-url-service}")
    private String shortUrlService;

    @Value("${youtube-service-url}")
    private String youtubeServiceUrl;

    @Value("${spring.profiles.active}")
    private String environment;

    public String getInternalBaseUrl() {
        return internalBaseUrl;
    }

    public String getExternalBaseUrl() {
        return externalBaseUrl;
    }

    public String getShortUrlService() {
        return shortUrlService;
    }

    public String getYoutubeServiceUrl() {
        return youtubeServiceUrl;
    }

    public String getCallbackBaseUrl() {
        return externalBaseUrl;
    }

    /**
     * 组装回调地址
     */
    public String getCallbackUrl(String path) {
        return externalBaseUrl + path;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isDevelopmentEnv() {
        return environment.equals(Environment.DEVELOPMENT);
    }

    public boolean isProductionEnv() {
        return environment.equals(Environment.PRODUCTION);
    }

}
