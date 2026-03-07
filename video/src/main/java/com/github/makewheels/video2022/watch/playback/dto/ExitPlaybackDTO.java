package com.github.makewheels.video2022.watch.playback.dto;

import lombok.Data;

@Data
public class ExitPlaybackDTO {
    private String playbackSessionId;
    private Long currentTimeMs;
    private Long totalPlayDurationMs;
    private String exitType;
    private String resolution;
}
