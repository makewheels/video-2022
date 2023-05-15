package com.github.makewheels.video2022.etc.exception;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.response.ErrorCode;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class ExceptionLog {
    @Id
    private String id;

    private ErrorCode errorCode;

    private String exceptionMessage;
    private String exceptionStackTrace;
    private Date createTime;

    public ExceptionLog() {
        this.createTime = new Date();
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
