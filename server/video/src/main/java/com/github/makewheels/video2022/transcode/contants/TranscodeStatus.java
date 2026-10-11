package com.github.makewheels.video2022.transcode.contants;

import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;

/**
 * 自建云函数链路的 Transcode 状态。与 MPS / 云函数历史状态共用 status 字段，
 * 通过 provider 区分语义。
 */
public class TranscodeStatus {
    /** 登记完成（LOCAL / 自建链路终态成功） */
    public static final String FINISHED = "FINISHED";
    /** 全部重试与兜底耗尽后的失败终态 */
    public static final String FAILED = "FAILED";
    /** 自建执行失败，等待重试或兜底 */
    public static final String RETRY_WAIT = "RETRY_WAIT";
    /** 自建重试耗尽，等待 MPS 兜底 */
    public static final String FALLBACK_PENDING = "FALLBACK_PENDING";
    /** 产物验证与登记中（原子认领登记权，防重复登记） */
    public static final String REGISTERING = "REGISTERING";

    /**
     * 判断：是不是，已结束的状态
     */
    public static boolean isFinishStatus(String status) {
        return status.equals(FINISHED) || status.equals(FAILED)
                || AliyunTranscodeStatus.isFinishStatus(status);
    }
}
