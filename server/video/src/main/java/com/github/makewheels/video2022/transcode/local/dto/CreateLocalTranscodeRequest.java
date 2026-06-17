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
}
