package com.github.makewheels.video2022.oss.osslog.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;

/**
 * OSS上的日志文件
 */
@Data
@Document
public class OssAccessLogFile {
    @Id
    private String id;
    @Indexed
    private String programBatchId;
    @Indexed
    private LocalDate logDate;

    @Indexed
    private String logFileKey;   // OSS上的日志文件的key
    @Indexed
    private String logFileName;  // 日志文件名
    @Indexed
    private Date logFileTime;    // 日志文件名上的时间
    private String logFileSequenceNumber;  // 日志文件名最后四位去重四位递增，例如0001

    private Date createTime;
    private Date updateTime;

    public OssAccessLogFile() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

}
