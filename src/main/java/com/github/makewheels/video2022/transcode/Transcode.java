package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Transcode {
    @Id
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String videoId;
    @Indexed
    private String taskId;
    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;
    @Indexed
    private String resolution;
    private String sourceKey;
    private String outputDir;

    private String m3u8Key;
    private String m3u8AccessUrl;
    private String m3u8CdnUrl;

    private JSONObject result;

}

