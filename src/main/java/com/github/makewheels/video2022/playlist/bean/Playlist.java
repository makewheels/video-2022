package com.github.makewheels.video2022.playlist.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Data
@Document
public class Playlist {
    @Id
    private String id;
    private String title;
    private String description;
    private String ownerId;
    private Date createTime;
    private Date updateTime;
    private List<String> itemIdList = new LinkedList<>();
}
