package com.github.makewheels.video2022.springboot.interceptor;

/**
 * 拦截器顺序
 */
public interface InterceptorOrder {
    int ADMIN_API_KEY = 1000;
    int PUT_TOKEN = 1001;
    int CHECK_TOKEN = 1002;
    int REQUEST_LOG = 1003;
    int OAUTH = 1004;
    int RATE_LIMIT = 1005;
}
