package com.github.makewheels.video2022.utils;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Data
@Document("ip_cache")
public class IpCache {
    @Id
    private String id;
    @Indexed(unique = true)
    private String ip;
    private String locationJson;
    @Indexed(expireAfterSeconds = 21600)
    private Date createdAt;
}
