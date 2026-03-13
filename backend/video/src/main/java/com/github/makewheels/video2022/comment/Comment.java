package com.github.makewheels.video2022.comment;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document("comment")
public class Comment {
    @Id
    private String id;

    @Indexed
    private String videoId;

    @Indexed
    private String userId;

    private String userPhone;
    private String userNickname;
    private String userAvatarUrl;

    private String content;

    /**
     * null = 顶级评论，非 null = 回复
     */
    @Indexed
    private String parentId;

    /**
     * 被回复的用户 ID（仅回复时有值）
     */
    private String replyToUserId;
    private String replyToUserPhone;
    private String replyToUserNickname;

    private Integer likeCount;

    private Integer replyCount;

    private Date createTime;
    private Date updateTime;
}
