package com.github.makewheels.video2022.transcode.bean;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeStatus;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

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
    private Integer width;
    private Integer height;
    private Integer averageBitrate;
    private Integer maxBitrate;

    private String sourceKey;
    private String m3u8Key;

    private JSONObject result;

    private String m3u8Content;

    private List<String> tsFileIds;

    public Transcode() {
        this.createTime = new Date();
        this.status = VideoStatus.CREATED;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    /**
     * 根据对应的provider判断是否是已结束状态
     */
    public boolean isFinishStatus() {
        switch (provider) {
            case TranscodeProvider.ALIYUN_MPS:
                return AliyunTranscodeStatus.isFinishStatus(status);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return CloudFunctionTranscodeStatus.isFinishedStatus(status);
        }
        return true;
    }

    /**
     * 判断是否是转码成功状态
     */
    public boolean isSuccessStatus() {
        switch (provider) {
            case TranscodeProvider.ALIYUN_MPS:
                return StringUtils.equals(status, AliyunTranscodeStatus.TranscodeSuccess);
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                return true;
        }
        return true;
    }

}

