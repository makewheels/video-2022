package com.github.makewheels.video2022.playlist;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Playlist {
    @Id
    private String id;
    private String userId;
    private Date createTime;

}
