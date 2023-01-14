package com.github.makewheels.video2022.exception;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class ExceptionLog {
    @Id
    private String id;
    private String stackTrace;
    private Date createTime;

    public ExceptionLog() {
        this.createTime = new Date();
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}