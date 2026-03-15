package com.github.makewheels.video2022.etc.app;

import lombok.Data;

@Data
public class PublishVersionRequest {
    private String platform;
    private Integer versionCode;
    private String versionName;
    private String versionInfo;
    private String downloadUrl;
    private Boolean isForceUpdate;
    private Integer minSupportedVersionCode;
}
