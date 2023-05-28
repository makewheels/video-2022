package com.github.makewheels.video2022.springboot.interceptor.requestlog;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class RequestLog {
    @Id
    private String id;

    private Request request;
    private Response response;

    private Long cost;
    private Date startTime;
    private Date endTime;

}
