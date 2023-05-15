package com.github.makewheels.video2022.file.operate;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 文件操作日志
 */
@Data
@Document
public class FileOperateLog {
    @Id
    private String id;
}
