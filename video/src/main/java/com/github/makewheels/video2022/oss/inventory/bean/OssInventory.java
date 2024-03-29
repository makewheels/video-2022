package com.github.makewheels.video2022.oss.inventory.bean;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * OSS清单，主表
 */
@Data
@Document
public class OssInventory {
    @Id
    private String id;

    @Indexed
    private String programBatchId;

    private String snapshotSourceBucket;  // 对这个存储桶打快照
    private String inventoryStorageBucket; // 保存清单的存储桶

    private List<String> gzOssKeys;

    private String manifestKey;
    private JSONObject manifest;

    /**
     * 阿里云生成快照时间
     * manifest.json文件里的creationTimestamp字段
     */
    private Date ossGenerationTime;

    /**
     * 清单生成日期
     * 是北京时间，例如 20231011
     */
    @Indexed
    private LocalDate inventoryGenerationDate;

    @Indexed
    private Date createTime;
    private Date updateTime;

    public OssInventory() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}
