package com.github.makewheels.video2022.watch.progress;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * 视频播放进度
 */
@Data
@CompoundIndexes({@CompoundIndex(def =
        "{'videoId': 1, 'viewerId': 1, 'clientId': 1}")})
public class Progress {
    @Id
    private String id;

    @Indexed
    private String videoId;

    //观众id，没登录就为空
    @Indexed
    private String viewerId;

    @Indexed
    private String clientId;

    //最后一次发送过来的时候的sessionId
    @Indexed
    private String lastSessionId;

    //视频进度，单位毫秒
    private Long progressInMillis;

    private Date createTime;
    private Date updateTime;

    public Progress() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
