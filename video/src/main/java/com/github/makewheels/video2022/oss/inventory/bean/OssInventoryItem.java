package com.github.makewheels.video2022.oss.inventory.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * oss清单
 * <a href="https://help.aliyun.com/zh/oss/user-guide/when-inventories-take-effect">清单</a>
 */
@Data
@Document
public class OssInventoryItem {
    @Id
    private String id;

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
    private Date aliyunGenerationTime;
    @Indexed
    private Integer inventoryGenerationDate;   // 清单生成日期

    private Date createTime;
    private Date updateTime;

    public OssInventoryItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
