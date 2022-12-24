package com.github.makewheels.video2022.bill;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Bill {
    @Id
    private String id;
    @Indexed
    private String videoId;
    @Indexed
    private String uploaderId;

    private Date createTime;
}
