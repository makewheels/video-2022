package com.github.makewheels.video2022.watch.play;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class WatchLog {
    @Id
    private String id;
    private String ip;
    private String videoId;
    private String clientId;
    private String sessionId;
    private String userAgent;
    private String videoStatus;

    private Date createTime;

    private JSONObject ipInfo;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
