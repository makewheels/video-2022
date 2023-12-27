package com.github.makewheels.video2022.video.constants;

import org.apache.commons.lang3.StringUtils;

public class VideoStatus {
    public static final String CREATED = "CREATED";
    public static final String UPLOADING = "UPLOADING";
    public static final String PREPARE_TRANSCODING = "PREPARE_TRANSCODING";
    public static final String TRANSCODING = "TRANSCODING";
    public static final String TRANSCODING_PARTLY_COMPLETE = "TRANSCODING_PARTLY_COMPLETE";
    public static final String PROCESSING_AFTER_TRANSCODE_COMPLETE = "PROCESSING_AFTER_TRANSCODE_COMPLETE";
    public static final String READY = "READY";

    /**
     * 是就绪状态
     */
    public static boolean isReady(String status) {
        return READY.equals(status);
    }

}
