package com.github.makewheels.video2022.file.access;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class FileAccessLog {
    @Id
    private String id;

    @Indexed
    private String fileId;

    @Indexed
    private String userId;
    @Indexed
    private String videoId;

    @Indexed
    private String transcodeId;
    @Indexed
    private String resolution;
    //ts碎片，在一个m3u8转码文件中的位置
    @Indexed
    private Integer tsSequence;

    // 63627b7e66445c2fe81c648a.mp4
    private String filename;

    // videos/62511690c3afe0646f9c670b/63627b7e66445c2fe81c648a/original/63627b7e66445c2fe81c648a.mp4
    private String key;

    private Long size;
    @Indexed
    private String etag;

    @Indexed
    private String fileType;

    @Indexed
    private String provider;
    @Indexed
    private String videoType;
    @Indexed
    private String storageClass;

    @Indexed
    private Date createTime;

    private String ip;
    private String clientId;
    private String sessionId;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
