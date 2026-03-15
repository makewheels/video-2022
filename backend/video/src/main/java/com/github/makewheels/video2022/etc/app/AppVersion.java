package com.github.makewheels.video2022.etc.app;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * App版本信息
 */
@Data
@Document
public class AppVersion {
    @Id
    private String id;

    @Indexed
    private String platform;

    private Integer versionCode;
    private String versionName;
    private String versionInfo;
    private String downloadUrl;
    private Boolean isForceUpdate;
    private Integer minSupportedVersionCode;

    private Date createTime;
    private Date updateTime;
}
