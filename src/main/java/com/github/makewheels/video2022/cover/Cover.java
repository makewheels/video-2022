package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.baidu.BaiduTranscodeStatus;
import com.github.makewheels.video2022.file.S3Provider;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document
public class Cover {
    @Id
    private String id;
    @Indexed
    private String userId;

    @Indexed
    private String videoId;

    @Indexed
    private String provider;

    @Indexed
    private String jobId;
    @Indexed
    private Date createTime;
    @Indexed
    private Date finishTime;
    @Indexed
    private String status;

    private String sourceKey;

    private String accessUrl;
    private String cdnUrl;

    private String extension;
    private String key;

    private JSONObject result;

    /**
     * 根据对应的provider判断是否是已结束状态
     *
     * @return
     */
    public boolean isFinishStatus() {
        if (provider.equals(S3Provider.ALIYUN_OSS)) {
            return AliyunTranscodeStatus.isFinishedStatus(status);
        } else if (provider.equals(S3Provider.BAIDU_BOS)) {
            return BaiduTranscodeStatus.isFinishedStatus(status);
        }
        return true;
    }
}
