package com.github.makewheels.video2022.oss.inventory;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * oss清单
 * <a href="https://help.aliyun.com/zh/oss/user-guide/when-inventories-take-effect">清单</a>
 */
@Data
public class OssInventory {
    private String bucketName;
    @Indexed
    private String objectName;
    private Long size;
    private String storageClass;
    private Date lastModifiedDate;
    @Indexed
    private String eTag;
    private Boolean isMultipartUploaded;
    private Boolean encryptionStatus;

    // 清单生成时间，单位秒，对应阿里云提供的json文件里的creationTimestamp字段
    @Indexed
    private Date inventoryGenerationTime;
    private Integer inventoryGenerationDate;   // 清单生成日期

    @Indexed
    private Date createTime;
    @Indexed
    private Date updateTime;
}
