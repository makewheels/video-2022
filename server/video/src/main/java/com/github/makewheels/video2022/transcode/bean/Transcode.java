package com.github.makewheels.video2022.transcode.bean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.contants.TranscodeStatus;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeStatus;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

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
    private String jobId;

    private String provider;

    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;
    @Indexed
    private String resolution;
    private Integer width;
    private Integer height;
    private Integer averageBitrate;
    private Integer maxBitrate;

    private String sourceKey;
    private String m3u8Key;

    private JSONObject result;

    private String m3u8Content;

    private List<String> tsFileIds;

    // ===== 自建云函数链路扩展字段，历史记录可为空 =====

    /** 关联的函数任务 ID（FcTask.id），MPS / LOCAL 为空 */
    @Indexed
    private String taskId;
    /** 当前生效的 provider（MPS 兜底后与 provider 不同） */
    private String currentProvider;
    /** MPS 兜底提交次数（0 或 1） */
    private Integer fallbackCount;
    /** 失败原因摘要（脱敏后） */
    private String errorMessage;
    /** 实际输出尺寸（可能因不放大而小于档位标称） */
    private Integer actualWidth;
    private Integer actualHeight;
    /** 实际输出帧率分数，如 "60000/1001" */
    private String actualFrameRate;
    /** 实际输出动态范围 */
    private String actualDynamicRange;
    /** HLS CODECS 属性值（如 "avc1.64001f,mp4a.40.2"），manifest 提供 */
    private String actualCodecs;

    public Transcode() {
        this.createTime = new Date();
        this.status = VideoStatus.CREATED;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    /**
     * 当前生效的 provider：MPS 兜底后 currentProvider 覆盖原始 provider
     */
    public String getActiveProvider() {
        return currentProvider != null ? currentProvider : provider;
    }

    /**
     * 根据当前生效的 provider 判断是否是已结束状态。
     * provider 未知时不视为结束（不能默认成功）。
     */
    public boolean isFinishStatus() {
        String active = getActiveProvider();
        if (active == null) return false;
        switch (active) {
            case TranscodeProvider.ALIYUN_MPS:
                return AliyunTranscodeStatus.isFinishStatus(status);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return CloudFunctionTranscodeStatus.isFinishedStatus(status);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU:
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU:
                return TranscodeStatus.FINISHED.equals(status)
                        || TranscodeStatus.FAILED.equals(status);
            case TranscodeProvider.LOCAL:
                // 本地转码回传完成才置 FINISHED，未完成的分辨率不能被算作已结束
                return TranscodeStatus.FINISHED.equals(status);
        }
        return false;
    }

    /**
     * 判断是否是转码成功状态。provider 未知时不视为成功。
     */
    public boolean isSuccessStatus() {
        String active = getActiveProvider();
        if (active == null) return false;
        switch (active) {
            case TranscodeProvider.ALIYUN_MPS:
                return StringUtils.equals(status, AliyunTranscodeStatus.TranscodeSuccess);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return true;
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_GPU:
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION_CPU:
            case TranscodeProvider.LOCAL:
                return TranscodeStatus.FINISHED.equals(status);
        }
        return false;
    }

}

