package com.github.makewheels.video2022.bill.bean;

import lombok.Data;

import java.util.Date;

/**
 * oss清单
 * <a href="https://help.aliyun.com/zh/oss/user-guide/when-inventories-take-effect">清单</a>
 */
@Data
public class OssInventory {
    private String bucketName;
    private String objectName;
    private Long size;
    private String storageClass;
    private Date lastModifiedDate;
    private String eTag;
    private Boolean isMultipartUploaded;
    private Boolean encryptionStatus;
}
