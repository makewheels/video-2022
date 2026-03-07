package com.github.makewheels.video2022.watch.playback;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 播放会话 — 记录一次完整的视频播放过程。
 * 每次打开播放页创建一条记录，退出时更新结束信息。
 */
@Data
@Document("playbackSession")
@CompoundIndex(name = "idx_video_user", def = "{'videoId': 1, 'userId': 1, 'createTime': -1}")
public class PlaybackSession {
    @Id
    private String id;

    @Indexed
    private String watchId;

    @Indexed
    private String videoId;

    private String userId;
    private String clientId;
    private String sessionId;

    private Date startTime;
    private Date endTime;

    /** 实际观看时长（毫秒），去除暂停时间 */
    private Long totalPlayDurationMs;

    /** 最远观看进度（毫秒） */
    private Long maxProgressMs;

    /** 当前播放位置（毫秒） */
    private Long currentProgressMs;

    /** 当前分辨率 */
    private String resolution;

    /** 退出类型：CLOSE_TAB / NAVIGATE_AWAY / PLAYING */
    private String exitType;

    /** 心跳次数 */
    private Integer heartbeatCount;

    private Date createTime;
    private Date updateTime;
}
