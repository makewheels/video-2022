package com.github.makewheels.video2022.api;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.Date;

/**
 * 调用第三方api日志，主要记录返回结果，用于之后问题排查
 */
@Data
public class Api {
    private String type;            //类型，比如：钉钉，阿里云 mps api
    private String code;            //第三方返回的状态码，统一转为String类型
    private String message;         //第三方返回的错误码
    private Boolean isSuccess;      //是否调用成功
    private JSONObject request;
    private JSONObject response;    //原始返回的json

    private Date startTime;
    private Date endTime;
    private Long cost;

}
