package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.user.client;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Client {
    @Id
    private String id;

    @Indexed
    private Date createTime;

    private String userAgent;
    private String ip;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
