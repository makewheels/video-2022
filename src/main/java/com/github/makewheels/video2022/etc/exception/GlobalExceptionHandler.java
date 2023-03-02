package com.github.makewheels.video2022.etc.exception;

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
    private void saveException(Exception exception) {
        ExceptionLog exceptionLog = new ExceptionLog();
        if (exception instanceof VideoException) {
            VideoException videoException = (VideoException) exception;
            exceptionLog.setErrorCode(videoException.getErrorCode());
        }
        String stackTrace = ExceptionUtils.getStackTrace(exception);
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
        //打印自定义错误码
        ErrorCode errorCode = videoException.getErrorCode();
        int code = errorCode.getCode();
        String value = errorCode.getValue();
        String message = videoException.getMessage();
        log.error("code = " + code + ", value = " + value + ", message = " + message);

        //打印错误堆栈
        videoException.printStackTrace();

        //把异常保存到数据库
        saveException(videoException);
        String stackTrace = ExceptionUtils.getStackTrace(videoException);
        return new Result<>(code, message, stackTrace);
    }

}
