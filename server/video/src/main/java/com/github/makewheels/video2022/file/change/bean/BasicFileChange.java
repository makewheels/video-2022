package com.github.makewheels.video2022.file.change.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 对象存储文件变更记录
 */
@Data
@Document
public class BasicFileChange {
    @Id
    private String id;

    @Indexed
    private String fileId;
    @Indexed
    private String key;

    private String method;

    private String beforeStorageClass;   // 改动前的存储类型
    private String afterStorageClass;    // 改动后的存储类型

    private String beforeAcl;   // 改动前的ACL
    private String afterAcl;    // 改动后的ACL


    private Date createTime;
}
