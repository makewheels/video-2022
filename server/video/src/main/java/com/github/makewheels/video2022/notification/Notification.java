package com.github.makewheels.video2022.notification;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document("notification")
public class Notification {
    @Id
    private String id;

    @Indexed
    private String toUserId;

    @Indexed
    private String fromUserId;

    private String type;

    private String content;

    private String relatedVideoId;

    private String relatedCommentId;

    @Indexed
    private boolean read;

    private Date createTime;
}
