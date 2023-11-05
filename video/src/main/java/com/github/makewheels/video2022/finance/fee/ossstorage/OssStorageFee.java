package com.github.makewheels.video2022.finance.fee.ossstorage;

import com.github.makewheels.video2022.finance.fee.base.BaseFee;
import com.github.makewheels.video2022.finance.fee.base.Fee;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * OSS存储费用
 */
@Getter
@Setter
@Document
public class OssStorageFee extends BaseFee implements Fee {
    @Indexed
    private String fileId;
    private String fileType;
    @Indexed
    private String key;
    private String storageClass;  // OSS存储类型，例如：标准存储，低频访问存储
    private Long fileSize;        // 文件大小，单位：字节

    private Date billTimeStart;   // 计费开始时间
    private Date billTimeEnd;     // 计费结束时间
}
