package com.github.makewheels.video2022.watch.playback.dto;

import lombok.Data;

@Data
public class StartPlaybackDTO {
    private String watchId;
    private String videoId;
    private String clientId;
    private String sessionId;
}
