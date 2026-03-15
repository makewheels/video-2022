package com.github.makewheels.video2022.video.like;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document("video_like")
@CompoundIndex(name = "video_user_idx", def = "{'videoId': 1, 'userId': 1}", unique = true)
public class VideoLike {
    @Id
    private String id;

    @Indexed
    private String videoId;

    @Indexed
    private String userId;

    /**
     * LIKE or DISLIKE
     */
    private String type;

    private Date createTime;
    private Date updateTime;
}
