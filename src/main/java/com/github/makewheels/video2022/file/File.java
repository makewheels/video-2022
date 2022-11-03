package com.github.makewheels.video2022.file;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class File {
    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed
    private String videoId;

    //原始文件名，只有用户上传的视频源文件才有
    private String originalFilename;
    // 63627b7e66445c2fe81c648a.mp4
    private String filename;

    // videos/62511690c3afe0646f9c670b/63627b7e66445c2fe81c648a/original/63627b7e66445c2fe81c648a.mp4
    private String key;
    private String extension;

    private Long size;
    @Indexed
    private String etag;

    @Indexed
    private String type;

    @Indexed
    private String provider;
    @Indexed
    private String videoType;
    @Indexed
    private String storageClass;

    @Indexed
    private String status;
    @Indexed
    private Date createTime;
    @Indexed
    private Date uploadTime;

}
