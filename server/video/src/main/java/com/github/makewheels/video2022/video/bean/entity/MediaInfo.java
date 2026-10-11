package com.github.makewheels.video2022.video.bean.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

/**
 * 媒体信息。基础字段来自 MPS 或自建 ffprobe 探测；
 * 扩展字段（帧率/色彩/位深/旋转/音轨等）仅自建探测链路填充，历史记录中可为空。
 */
@Data
public class MediaInfo {
    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    private Integer bitrate;
    private Long duration;      //视频时长，单位毫秒

    private JSONObject response;

    // ===== 以下为自建 ffprobe 探测补充字段，可为空 =====

    /** 显示宽高：应用旋转与 SAR 后的实际显示尺寸 */
    private Integer displayWidth;
    private Integer displayHeight;

    /** 原始帧率分数，如 "60000/1001"；VFR 时为平均帧率 */
    private String frameRate;
    /** 帧率模式：CFR / VFR */
    private String frameRateMode;
    /** 采样宽高比，如 "1:1" */
    private String sar;
    /** 旋转角度（display matrix），0/90/180/270 */
    private Integer rotation;

    /** 像素格式，如 yuv420p / yuv420p10le */
    private String pixFmt;
    /** 位深：8/10/12 */
    private Integer bitDepth;

    private String colorPrimaries;
    private String colorTransfer;
    private String colorSpace;
    private String colorRange;

    /** 动态范围：SDR / HDR10 / HLG / PQ / HDR10+ / DOLBY_VISION；无法判定时为 UNKNOWN */
    private String dynamicRange;

    /** 音轨数量 */
    private Integer audioTrackCount;
    /** 全部音轨信息（语言/声道/codec），探测原始结构摘要 */
    private JSONObject audioTracks;
}
