package com.github.makewheels.video2022.etc.springboot.interceptor.requestlog;

import lombok.Data;

import java.util.Map;

@Data
public class Response {
    private Integer httpStatus;
    private Map<String, Object> headerMap;
    private String body;
}
