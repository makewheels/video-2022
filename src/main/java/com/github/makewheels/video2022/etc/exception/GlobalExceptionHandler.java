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

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Result<Void> exceptionHandler(Exception e) {
        e.printStackTrace();

        //保存错误到数据库
        ExceptionLog exceptionLog = new ExceptionLog();
        String stackTrace = ExceptionUtils.getStackTrace(e);
        exceptionLog.setStackTrace(stackTrace);
        mongoTemplate.save(exceptionLog);

        //返回前端错误信息
        return new Result<>(ErrorCode.FAIL.getCode(), e.getMessage());
    }

}
