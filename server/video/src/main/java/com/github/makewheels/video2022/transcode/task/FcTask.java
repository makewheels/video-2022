package com.github.makewheels.video2022.transcode.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 自建云函数任务：一次函数调用对应一条记录。
 * <p>
 * 承载 taskId / attemptId / deadline / 重试计数，回调与超时恢复都以此为准。
 * operation = PROBE 时 refId 为空（结果写入 video.mediaInfo）；
 * operation = TRANSCODE 时 refId 为 transcodeId；
 * operation = COVER 时 refId 为 coverId。
 */
@Data
@Document("fcTask")
public class FcTask {
    @Id
    private String id;

    /** PROBE / COVER / TRANSCODE */
    private String operation;

    @Indexed
    private String videoId;
    /** TRANSCODE → transcodeId；COVER → coverId；PROBE → null */
    private String refId;

    /** 当前尝试轮次标识，提交时生成；回调必须匹配才被接受 */
    @Indexed
    private String attemptId;
    /** 已发起的尝试次数（含首次） */
    private Integer attemptCount;
    /** 允许的最大自建尝试次数（首次 + 1 次重试） */
    private Integer maxAttempts;

    /** 提交的函数提供方（TranscodeProvider 常量） */
    private String provider;
    /** FC 异步调用受理返回的 request id */
    private String fcRequestId;

    /** FcTaskStatus 常量 */
    private String status;
    /** 超时恢复截止时间；超过即由恢复流程原子认领 */
    @Indexed
    private Date deadline;

    /** 脱敏后的失败摘要 */
    private String errorMessage;

    /** 提交时的协议 payload 快照（重试与超时恢复重提交用；不含秘密） */
    private JSONObject payloadSnapshot;

    @Indexed
    private Date createTime;
    private Date finishTime;

    public FcTask() {
        this.createTime = new Date();
        this.status = FcTaskStatus.CREATED;
        this.attemptCount = 0;
        this.maxAttempts = 2;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
