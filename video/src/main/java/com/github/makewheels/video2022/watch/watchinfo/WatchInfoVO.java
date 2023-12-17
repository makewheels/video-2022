package com.github.makewheels.video2022.watch.watchinfo;

import lombok.Data;

@Data
public class WatchInfoVO {
    private String videoId;
    private String coverUrl;
    private String multivariantPlaylistUrl;
    private String videoStatus;
    private Long progressInMillis;
}
