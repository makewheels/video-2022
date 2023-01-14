package com.github.makewheels.video2022.etc.exception;

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
}
