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

    private String nickname;
    private String avatarFileId;  // 头像文件ID，用于生成签名URL
    private String avatarUrl;     // 兼容旧数据，后续可废弃
    private String bannerUrl;
    private String bio;
    private Long subscriberCount;
    private Long videoCount;

    public User() {
        this.createTime = new Date();
        this.updateTime = new Date();
        this.subscriberCount = 0L;
        this.videoCount = 0L;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
