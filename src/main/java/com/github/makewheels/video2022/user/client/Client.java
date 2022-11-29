package com.github.makewheels.video2022.user.client;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Client {
    @Id
    private String id;

    @Indexed
    private Date createTime;

    private String userAgent;
    private String ip;

}
