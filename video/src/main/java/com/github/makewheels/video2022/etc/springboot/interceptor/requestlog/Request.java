package com.github.makewheels.video2022.etc.springboot.interceptor.requestlog;

import lombok.Data;

import java.util.Map;

@Data
public class Request {
    private String url;
    private String path;
    private String method;
    private String queryString;
    private Map<String, Object> headerMap;
    private String ip;
    private String userAgent;
    private String body;
}
