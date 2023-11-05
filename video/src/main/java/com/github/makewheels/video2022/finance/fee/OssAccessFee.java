package com.github.makewheels.video2022.finance.fee;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * OSS访问文件费用
 */
@Getter
@Setter
@Document
public class OssAccessFee extends BaseFee {
    @Indexed
    private String fileId;
    @Indexed
    private String key;
    private String storageClass;  // OSS存储类型，例如：标准存储，低频访问存储
    private Long fileSize;        // 文件大小，单位：字节

    @Indexed
    private Date billTime;        // 计费时间
}
