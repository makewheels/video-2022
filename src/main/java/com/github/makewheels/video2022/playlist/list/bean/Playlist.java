package com.github.makewheels.video2022.playlist.list.bean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * 播放列表
 */
@Getter
@Setter
@Document
public class Playlist {
    @Id
    private String id;

    private String title;
    private String description;
    @Indexed
    private String ownerId;
    @Indexed
    private String visibility;
    @Indexed
    private Boolean isDelete = false;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    private List<IdBean> videoList;

    public Playlist() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
