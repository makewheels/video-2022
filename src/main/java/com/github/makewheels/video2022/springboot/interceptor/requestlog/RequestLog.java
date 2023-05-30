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

    private Date startTime;  // 调用接口开始时间
    private Date endTime;    // 调用接口结束时间
    private Long timeCost;   // 调用接口耗时

}
