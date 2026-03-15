package com.github.makewheels.video2022.openapi.oauth.dto;

import lombok.Data;

import java.util.Date;

@Data
public class DeveloperVO {
    private String id;
    private String email;
    private String name;
    private String company;
    private String status;
    private Date createTime;
}
