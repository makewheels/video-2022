package com.github.makewheels.video2022.oss.inventory.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;

/**
 * OSS清单，子项item表
 * <a href="https://help.aliyun.com/zh/oss/user-guide/when-inventories-take-effect">清单</a>
 */
@Data
@Document
public class OssInventoryItem {
    @Id
    private String id;

    @Indexed
    private String programBatchId;

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

    /**
     * 主表OssInventory的id
     */
    @Indexed
    private String inventoryId;

    /**
     * 阿里云生成快照时间
     * manifest.json文件里的creationTimestamp字段
     */
    @Indexed
    private Date ossGenerationTime;

    /**
     * 清单生成日期
     * 是北京时间，例如 20231011
     */
    @Indexed
    private LocalDate inventoryGenerationDate;   // 清单生成日期

    @Indexed
    private Date createTime;
    private Date updateTime;

    public OssInventoryItem() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
