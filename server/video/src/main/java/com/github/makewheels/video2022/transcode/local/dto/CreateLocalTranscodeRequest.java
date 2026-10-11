package com.github.makewheels.video2022.transcode.local.dto;

import lombok.Data;

/**
 * 本地转码：创建一档分辨率的转码任务请求。
 * 由客户端用本机 ffprobe 探测源片后填入媒体信息，服务端据此登记并返回上传凭证与目标 OSS key。
 */
@Data
public class CreateLocalTranscodeRequest {
    /**
     * 视频 ID
     */
    private String videoId;
    /**
     * 目标分辨率，见 Resolution（720p / 1080p / 480p）
     */
    private String resolution;

    // ↓↓↓ 源片媒体信息（首次调用时写入 Video.mediaInfo，多档调用幂等）
    /**
     * 源片宽
     */
    private Integer width;
    /**
     * 源片高
     */
    private Integer height;
    /**
     * 源片时长，单位毫秒（用于回调时计算平均码率，必填）
     */
    private Long durationMs;
    /**
     * 视频编码，如 h264
     */
    private String videoCodec;
    /**
     * 音频编码，如 aac（无音频可空）
     */
    private String audioCodec;
    /**
     * 源片整体码率，单位 kbps（与 MPS 口径一致）
     */
    private Integer bitrate;

    // ↓↓↓ 媒体保留扩展字段（可选，缺省不写）。
    // 旋转后的显示尺寸（决定档位与是否缩放）
    private Integer displayWidth;
    private Integer displayHeight;
    /** 帧率（数值）与模式 CFR/VFR */
    private Double frameRate;
    private String frameRateMode;
    private String sar;
    private Integer rotation;
    private String pixFmt;
    private Integer bitDepth;
    private String colorPrimaries;
    private String colorTransfer;
    private String colorSpace;
    private String colorRange;
    /** SDR / HDR10 / HLG / PQ / DOLBY_VISION / UNKNOWN；缺省按 SDR 记录 */
    private String dynamicRange;
    private Integer audioTrackCount;
}
