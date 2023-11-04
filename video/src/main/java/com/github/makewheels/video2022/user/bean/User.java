package com.github.makewheels.video2022.user.bean;

import com.alibaba.fastjson.JSON;
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

    private String registerChannel;

    @Indexed
    private Date createTime;
    private Date updateTime;

    @Indexed
    private String token;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
