package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.bean.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 视频是否删除信息
 */
@Data
public class StorageStatus {
    public static final String FIELD_NAME = "storageStatus";

    @Indexed
    private Date expireTime;                    //过期时间
    @Indexed
    private Boolean permanent;                //是否是永久视频
    private Boolean rawFileDeleted;      //源视频是否已删除
    private Boolean transcodeFilesDeleted;    //ts转码文件是否已删除
    private Date deleteTime;                    //什么时候删的

    public StorageStatus() {
        this.rawFileDeleted = false;
        this.transcodeFilesDeleted = false;
        this.permanent = false;
    }
}
