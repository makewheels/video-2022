package com.github.makewheels.video2022.oss.osslog.bean;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GenerateOssAccessLogDTO {
    private LocalDate date;
    private String programBatchId;
}
