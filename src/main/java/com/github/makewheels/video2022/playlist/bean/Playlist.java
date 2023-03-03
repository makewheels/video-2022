package com.github.makewheels.video2022.playlist.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document
public class Playlist {
    @Id
    private String id;
    private String title;
    private String description;
    @Indexed
    private String ownerId;
    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
    private List<IdBean> idBeanList;

    public Playlist() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
