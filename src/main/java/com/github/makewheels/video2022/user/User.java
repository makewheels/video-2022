package com.github.makewheels.video2022.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class User {
    @Id
    private String id;

    @Indexed
    private String phone;
    @Indexed
    private Date createTime;

    @Indexed
    private String token;

}
