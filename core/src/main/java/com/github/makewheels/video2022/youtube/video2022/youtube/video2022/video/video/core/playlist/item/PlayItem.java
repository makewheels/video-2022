package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.playlist.item;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 播放列表中的视频
 */
@Data
@Document
public class PlayItem {
    @Id
    private String id;

    private String playlistId;
    private String videoId;
    private String owner;

    private Boolean isDelete = false;

    private Date createTime;
    private Date updateTime;

    public PlayItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
