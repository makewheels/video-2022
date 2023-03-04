package com.github.makewheels.video2022.playlist.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class PlaylistItem {
    @Id
    private String id;
    private String playlistId;
    private String videoId;
    private String owner;
    private Date createTime;
    private Date updateTime;

    public PlaylistItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
