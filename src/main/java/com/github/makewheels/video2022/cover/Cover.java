package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Cover {
    @Id
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String videoId;

    @Indexed
    private String provider;
    @Indexed
    private String fileId;

    @Indexed
    private String jobId;
    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;

    private String sourceKey;

    private String accessUrl;

    private String extension;
    private String key;

    private JSONObject result;

    public Cover() {
        this.createTime = new Date();
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
