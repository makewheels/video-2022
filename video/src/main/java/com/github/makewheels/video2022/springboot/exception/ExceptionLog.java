package com.github.makewheels.video2022.springboot.exception;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class ExceptionLog {
    @Id
    private String id;

    private Integer systemErrorCode;
    private String systemErrorMessage;

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
