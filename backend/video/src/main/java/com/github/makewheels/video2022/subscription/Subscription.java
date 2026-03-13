package com.github.makewheels.video2022.subscription;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document("subscription")
@CompoundIndex(name = "uk_user_channel", def = "{'userId': 1, 'channelUserId': 1}", unique = true)
public class Subscription {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String channelUserId;

    private Date createTime;
}
