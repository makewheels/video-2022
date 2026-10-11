package com.github.makewheels.video2022.transcode.task;

/**
 * 自建云函数任务状态。
 * <p>
 * QUEUED → SUBMITTED → (SUCCEEDED | RETRY_WAIT → SUBMITTED | FALLBACK_PENDING → SUCCEEDED | FAILED)
 * 终态只有 SUCCEEDED 与 FAILED；RETRY_WAIT / FALLBACK_PENDING 由超时恢复或回调驱动流转。
 */
public class FcTaskStatus {
    public static final String CREATED = "CREATED";
    /** 已提交到函数（受理响应已确认） */
    public static final String SUBMITTED = "SUBMITTED";
    public static final String RETRY_WAIT = "RETRY_WAIT";
    public static final String FALLBACK_PENDING = "FALLBACK_PENDING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    public static boolean isFinal(String status) {
        return SUCCEEDED.equals(status) || FAILED.equals(status);
    }
}
