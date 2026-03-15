package com.github.makewheels.video2022.openapi.oauth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Developer {
    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String name;
    private String company;
    private String status;

    private Date createTime;
    private Date updateTime;

    public Developer() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.status = "active";
    }
}
