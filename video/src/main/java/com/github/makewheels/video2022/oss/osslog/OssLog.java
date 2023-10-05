package com.github.makewheels.video2022.oss.osslog;

import lombok.Data;

import java.util.Date;

/**
 * oss日志
 * <a href="https://help.aliyun.com/zh/oss/user-guide/logging">日志转存</a>
 */
@Data
public class OssLog {
    private String remoteIp;
    private String reserved1;
    private String reserved2;
    private Date time;
    private String requestUrl;
    private Integer httpStatus;
    private Long sentBytes;
    private Long requestTime;
    private String referer;
    private String userAgent;
    private String hostName;
    private String requestId;
    private Boolean loggingFlag;
    private String requesterAliyunId;
    private String operation;
    private String bucketName;
    private String objectName;
    private Long objectSize;
    private Long serverCostTime;
    private String errorCode;
    private Integer requestLength;
    private String userId;
    private Long deltaDataSize;
    private String syncRequest;
    private String storageClass;
    private String targetStorageClass;
    private String transmissionAccelerationAccessPoint;
    private String accessKeyId;
}
