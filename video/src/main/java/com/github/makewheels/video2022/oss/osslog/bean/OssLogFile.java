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
public class OssLogFile {
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
    private Date logFileTime;   // 日志文件名上的时间
    @Indexed
    private String logFileUniqueString;  // 日志文件的唯一标识

    @Indexed
    private Date createTime;
    private Date updateTime;

}
