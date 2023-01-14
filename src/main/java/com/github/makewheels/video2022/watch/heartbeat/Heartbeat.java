package com.github.makewheels.video2022.watch.heartbeat;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class Heartbeat {
    @Id
    private String id;

    private String videoId;
    private String clientId;
    private String sessionId;
    private String viewerId;
    private String videoStatus;

    private String playerProvider;
    private Date clientTime;
    private Date createTime;

    private String type;
    private String event;
    private Long playerTime;
    private String playerStatus;
    private BigDecimal playerVolume;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
