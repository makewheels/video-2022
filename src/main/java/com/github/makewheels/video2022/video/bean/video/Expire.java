package com.github.makewheels.video2022.video.bean.video;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 视频过期
 */
@Data
public class Expire {
    @Indexed
    private Date expireTime;                    //过期时间
    @Indexed
    private Boolean isPermanent;                //是否是永久视频
    private Boolean isOriginalFileDeleted;      //源视频是否已删除
    private Boolean isTranscodeFilesDeleted;    //ts转码文件是否已删除
    private Date deleteTime;                    //什么时候删的
}
