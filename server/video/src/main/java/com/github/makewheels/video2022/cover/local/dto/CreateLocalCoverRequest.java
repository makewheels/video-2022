package com.github.makewheels.video2022.cover.local.dto;

import lombok.Data;

/**
 * 本地封面：创建封面记录请求。客户端用本机 FFmpeg 截帧后回传。
 */
@Data
public class CreateLocalCoverRequest {
    /**
     * 视频 ID
     */
    private String videoId;
    /**
     * 封面图片扩展名，默认 jpg
     */
    private String extension;
}
