package com.github.makewheels.video2022.watch.watchinfo;

import lombok.Data;

import java.util.List;

@Data
public class WatchInfoVO {
    private String videoId;
    private String coverUrl;
    private List<PlayUrl> playUrlList;
    private String multivariantPlaylistUrl;
    private String videoStatus;
}
