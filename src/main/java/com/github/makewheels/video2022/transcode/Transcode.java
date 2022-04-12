package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.video.Provider;
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

    /**
     * 根据对应的provider判断是否是已结束状态
     *
     * @return
     */
    public boolean isFinishStatus() {
        if (provider.equals(Provider.ALIYUN)) {
            return AliyunTranscodeStatus.isFinishedStatus(status);
        } else if (provider.equals(Provider.BAIDU)) {
            return BaiduTranscodeStatus.isFinishedStatus(status);
        }
        return true;
    }

    /**
     * 判断是否是转码成功状态
     *
     * @return
     */
    public boolean isSuccess() {
        if (provider.equals(Provider.ALIYUN)) {
            return StringUtils.equals(status, AliyunTranscodeStatus.TranscodeSuccess);
        } else if (provider.equals(Provider.BAIDU)) {
            return StringUtils.equals(status, BaiduTranscodeStatus.SUCCESS);
        }
        return true;
    }

}

