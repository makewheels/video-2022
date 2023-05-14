package com.github.makewheels.video2022.video.bean.video;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 视频存储状态，可能过期删除或者在低频存储
 */
@Data
public class StorageStatus {
    @Indexed
    private Date expireTime;                    //过期时间
    @Indexed
    private Boolean isPermanent;                //是否是永久视频
    private Boolean isOriginalFileDeleted;      //源视频是否已删除
    private Boolean isTranscodeFilesDeleted;    //ts转码文件是否已删除
    private Date deleteTime;                    //什么时候删的
}
