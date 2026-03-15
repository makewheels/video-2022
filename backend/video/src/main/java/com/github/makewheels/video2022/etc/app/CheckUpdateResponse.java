package com.github.makewheels.video2022.etc.app;

import lombok.Data;

@Data
public class CheckUpdateResponse {
    private Boolean hasUpdate;
    private Integer versionCode;
    private String versionName;
    private String versionInfo;
    private String downloadUrl;
    private Boolean isForceUpdate;
}
