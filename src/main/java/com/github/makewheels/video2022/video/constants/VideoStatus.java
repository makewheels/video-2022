package com.github.makewheels.video2022.video.constants;

import org.apache.commons.lang3.StringUtils;

public interface VideoStatus {
    String CREATED = "CREATED";
    String PREPARE_TRANSCODING= "PREPARE_TRANSCODING";
    String TRANSCODING = "TRANSCODING";
    String TRANSCODING_PARTLY_COMPLETE = "TRANSCODING_PARTLY_COMPLETED";
    String READY = "READY";

    /**
     * 是就绪状态
     */
    static boolean isReady(String status) {
        return READY.equals(status);
    }

    /**
     * 不是就绪状态
     */
    static boolean isNotReady(String status) {
        return StringUtils.equalsAny(status, CREATED, TRANSCODING, TRANSCODING_PARTLY_COMPLETE);
    }
}
