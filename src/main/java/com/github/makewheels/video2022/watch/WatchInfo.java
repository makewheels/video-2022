package com.github.makewheels.video2022.watch;

import lombok.Data;

import java.util.List;

@Data
public class WatchInfo {
    private String videoId;
    private String coverUrl;
    private List<PlayUrl> playUrlList;
    private String videoStatus;
}
