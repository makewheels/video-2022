package com.github.makewheels.video2022.watch.heartbeat;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Document
@CompoundIndexes({@CompoundIndex(def =
        "{'videoId': 1, 'viewerId': 1, 'clientId': 1, 'createTime': 1}")})
public class Heartbeat {
    @Id
    private String id;

    private String videoId;
    private String clientId;
    private String sessionId;
    private String viewerId;
    private String videoStatus;

    private String playerProvider;
    private Date clientTime;
    private Date createTime;

    private String type;
    private String event;
    private Long playerTime;
    private String playerStatus;
    private BigDecimal playerVolume;

}
