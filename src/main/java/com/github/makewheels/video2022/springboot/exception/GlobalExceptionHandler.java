package com.github.makewheels.video2022.springboot.exception;

import com.github.makewheels.video2022.etc.ding.NotificationService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.system.response.ErrorCode;
import com.github.makewheels.video2022.system.response.Result;
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
    @Resource
    private NotificationService notificationService;
    @Resource
    private EnvironmentService environmentService;

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Result<Object> exceptionHandler(Exception exception) {
        exception.printStackTrace();
        handleException(exception);
        String stackTrace = ExceptionUtils.getStackTrace(exception);
        return new Result<>(ErrorCode.FAIL.getCode(), exception.getMessage(), stackTrace);
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

        //打印错误堆栈
        videoException.printStackTrace();

        //把异常保存到数据库
        handleException(videoException);
        String stackTrace = ExceptionUtils.getStackTrace(videoException);
        return new Result<>(code, message, stackTrace);
    }

    /**
     * 最终处理异常
     */
    private void handleException(Exception exception) {
        ExceptionLog exceptionLog = new ExceptionLog();
        if (exception instanceof VideoException) {
            VideoException videoException = (VideoException) exception;
            exceptionLog.setErrorCode(videoException.getErrorCode());
        }
        exceptionLog.setExceptionStackTrace(ExceptionUtils.getStackTrace(exception));

        //保存到数据库
        mongoTemplate.save(exceptionLog);

        //发送钉钉消息
        //只有生产才发错误消息到钉钉
        if (environmentService.isProductionEnv()) {
            notificationService.sendExceptionMessage(exception, exceptionLog);
        }
    }


}
