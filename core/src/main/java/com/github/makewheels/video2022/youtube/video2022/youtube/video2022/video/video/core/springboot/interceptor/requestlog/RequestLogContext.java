package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.springboot.interceptor.requestlog;

public class RequestLogContext {
    private static final ThreadLocal<RequestLog> requestLogThreadLocal = new ThreadLocal<>();

    public static RequestLog getRequestLog() {
        return requestLogThreadLocal.get();
    }

    public static void setRequestLog(RequestLog requestLog) {
        requestLogThreadLocal.set(requestLog);
    }

    public static void removeRequestLog() {
        requestLogThreadLocal.remove();
    }
}
