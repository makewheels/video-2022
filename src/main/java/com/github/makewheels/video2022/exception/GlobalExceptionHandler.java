package com.github.makewheels.video2022.exception;

import com.github.makewheels.video2022.etc.response.ErrorCode;
import com.github.makewheels.video2022.etc.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

/**
 * 全局异常处理
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 保存Exception到数据库
     */
    private void saveException(Exception e) {
        ExceptionLog exceptionLog = new ExceptionLog();
        String stackTrace = ExceptionUtils.getStackTrace(e);
        exceptionLog.setStackTrace(stackTrace);
        mongoTemplate.save(exceptionLog);
    }

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Result<Void> exceptionHandler(Exception exception) {
        exception.printStackTrace();
        saveException(exception);
        return new Result<>(ErrorCode.FAIL.getCode(), exception.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(VideoException.class)
    public Result<Object> exceptionHandler(VideoException videoException) {
        videoException.printStackTrace();
        saveException(videoException);
        ErrorCode errorCode = videoException.getErrorCode();
        String stackTrace = ExceptionUtils.getStackTrace(videoException);
        return new Result<>(errorCode.getCode(), errorCode.getValue(), stackTrace);
    }

}
