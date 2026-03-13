package com.github.makewheels.video2022.user.bean;

import lombok.Data;

@Data
public class ChannelVO {
    private String userId;
    private String nickname;
    private String avatarUrl;
    private String bannerUrl;
    private String bio;
    private Long subscriberCount;
    private Long videoCount;
    private Boolean isSubscribed;
}
