package com.github.makewheels.video2022.file;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class File {
    @Id
    private String id;

    private String key;
    private String accessUrl;
    private String cdnUrl;
    private Long size;
    private Date createTime;
}
