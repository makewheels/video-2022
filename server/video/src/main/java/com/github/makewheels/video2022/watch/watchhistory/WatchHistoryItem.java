package com.github.makewheels.video2022.watch.watchhistory;

import lombok.Data;

import java.util.Date;

/**
 * 观看历史列表项
 */
@Data
public class WatchHistoryItem {
    private String videoId;
    private String title;
    private String coverUrl;
    private Date watchTime;
}
