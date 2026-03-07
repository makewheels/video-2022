package com.github.makewheels.video2022.comment;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document("comment_like")
@CompoundIndex(name = "comment_user_idx", def = "{'commentId': 1, 'userId': 1}", unique = true)
public class CommentLike {
    @Id
    private String id;

    @Indexed
    private String commentId;

    @Indexed
    private String userId;

    private Date createTime;
}
