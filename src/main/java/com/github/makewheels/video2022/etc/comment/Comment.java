package com.github.makewheels.video2022.etc.comment;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class Comment {
    @Id
    private String id;

    private String videoId;

    private String clientId;
    private String sessionId;

    private String type;
    private String content;

    private String createTime;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
