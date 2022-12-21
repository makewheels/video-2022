package com.github.makewheels.video2022.watch.progress;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

@Data
public class Progress {
    @Id
    private String id;

    @Indexed
    private String videoId;
    @Indexed
    private String clientId;

    //最后一次发送过来的时候的sessionId
    @Indexed
    private String lastSessionId;

    //观众id，没登录就为空
    @Indexed
    private String viewerId;

    //视频进度，现在播放到第几毫秒了
    private Long playerTime;

    private Date createTime;
    private Date updateTime;
}