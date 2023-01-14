package com.github.makewheels.video2022.etc.exception;

import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @Resource
    private MongoTemplate mongoTemplate;

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Result<Void> exceptionHandler(HttpServletRequest request, Exception e) {
        e.printStackTrace();
        return new Result<>(ErrorCode.FAIL.getCode(), e.getMessage());
    }
}
