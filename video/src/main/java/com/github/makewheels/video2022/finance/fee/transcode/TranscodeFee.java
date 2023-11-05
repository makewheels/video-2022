package com.github.makewheels.video2022.finance.fee.transcode;

import com.github.makewheels.video2022.finance.fee.BaseFee;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 视频转码费用
 */
@Getter
@Setter
@Document
public class TranscodeFee extends BaseFee {
    @Indexed
    private String transcodeId;
    private String resolution;
    private Long duration;      //视频时长，单位毫秒
    private String provider;    // 供应商，例如阿里云接口，云函数

    @Indexed
    private Date billTime;      // 计费时间
}
