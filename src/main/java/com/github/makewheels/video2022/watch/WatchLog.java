package com.github.makewheels.video2022.watch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class WatchLog {
    @Id
    private String id;
    private String userId;
    private String ip;
    private String videoId;
    private String clientId;
    private Date createTime;
}
