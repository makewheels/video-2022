package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.baidu.BaiduTranscodeStatus;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeStatus;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Transcode {
    @Id
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String videoId;
    @Indexed
    private String jobId;

    private String provider;

    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;
    @Indexed
    private String resolution;
    private String sourceKey;

    private String m3u8Key;
    private String m3u8AccessUrl;
    private String m3u8CdnUrl;

    private JSONObject result;

    private String m3u8;

    /**
     * 根据对应的provider判断是否是已结束状态
     *
     * @return
     */
    public boolean isFinishStatus() {
        switch (provider) {
            case TranscodeProvider.ALIYUN_MPS:
                return AliyunTranscodeStatus.isFinishedStatus(status);
            case TranscodeProvider.BAIDU_MCP:
                return BaiduTranscodeStatus.isFinishedStatus(status);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return CloudFunctionTranscodeStatus.isFinishedStatus(status);
        }
        return true;
    }

    /**
     * 判断是否是转码成功状态
     *
     * @return
     */
    public boolean isSuccessStatus() {
        switch (provider) {
            case TranscodeProvider.ALIYUN_MPS:
                return StringUtils.equals(status, AliyunTranscodeStatus.TranscodeSuccess);
            case TranscodeProvider.BAIDU_MCP:
                return StringUtils.equals(status, BaiduTranscodeStatus.SUCCESS);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return true;
        }
        return true;
    }

}

