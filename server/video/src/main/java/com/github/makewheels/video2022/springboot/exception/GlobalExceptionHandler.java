package com.github.makewheels.video2022.springboot.exception;

import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.annotation.Resource;

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
    public Result<Object> exceptionHandler(Exception exception) {
        log.error("未处理异常", exception);
        handleException(exception);
        return new Result<>(ErrorCode.FAIL.getCode(), ErrorCode.FAIL.getMessage(), null);
    }

    @ResponseBody
    @ExceptionHandler(VideoException.class)
    public Result<Object> exceptionHandler(VideoException videoException) {
        //打印自定义错误码
        ErrorCode errorCode = videoException.getErrorCode();
        int code = errorCode.getCode();
        String value = errorCode.getMessage();
        String message = errorCode.getMessage();
        log.error("code = " + code + ", value = " + value + ", message = " + message);

        //把异常保存到数据库
        handleException(videoException);
        return new Result<>(code, message, null);
    }

    /**
     * 最终处理异常
     */
    private void handleException(Exception exception) {
        ExceptionLog exceptionLog = new ExceptionLog();
        if (exception instanceof VideoException) {
            VideoException videoException = (VideoException) exception;
            exceptionLog.setSystemErrorCode(videoException.getErrorCode().getCode());
            exceptionLog.setSystemErrorMessage(videoException.getErrorCode().getMessage());
        }
        exceptionLog.setExceptionMessage(exception.getMessage());
        exceptionLog.setExceptionStackTrace(ExceptionUtils.getStackTrace(exception));

        //保存到数据库
        mongoTemplate.save(exceptionLog);
    }


}
