package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.etc.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 调用第三方api日志，主要记录返回结果，用于之后问题排查
 */
@Data
@Document
public class Api {
    @Id
    private String id;

    private String type;            //类型，比如：钉钉，阿里云 mps api
    private String code;            //第三方返回的状态码，统一转为String类型
    private String message;         //第三方返回的message
    private Boolean isSuccess;      //是否调用成功
    private JSONObject request;
    private JSONObject response;    //原始返回的json

    private Date startTime;
    private Date endTime;
    private Long cost;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
