package com.github.makewheels.video2022.oss.osslog.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class OssLogFile {
    @Id
    private String id;
    @Indexed
    private String programBatchId;
    @Indexed
    private Date createTime;
    private Date updateTime;

}
