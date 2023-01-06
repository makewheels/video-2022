package com.github.makewheels.video2022.startup;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * SpringBoot启动日志
 */
@Data
@Document
public class StartupLog {
    @Id
    private String id;

    private Date createTime;

    private JSONObject systemInfo;
}
