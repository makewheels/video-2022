package com.github.makewheels.video2022.watch.playback.dto;

import lombok.Data;

@Data
public class HeartbeatPlaybackDTO {
    private String playbackSessionId;
    private Long currentTimeMs;
    private Boolean isPlaying;
    private String resolution;
    private Long totalPlayDurationMs;
}
