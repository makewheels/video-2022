package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;


@Data
@Document
public class Transcode {
    @Id
    private String id;

    private String userId;

    private String videoId;
    private String taskId;
    private Date createTime;
    private Date finishTime;
    private String status;

    private String resolution;
    private String sourceKey;
    private String outputDir;

    private String m3u8Key;
    private String m3u8AccessUrl;
    private String m3u8CdnUrl;

    private JSONObject result;


}

