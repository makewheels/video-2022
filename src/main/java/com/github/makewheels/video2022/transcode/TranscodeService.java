package com.github.makewheels.video2022.transcode;

import com.baidubce.BceClientConfiguration;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class TranscodeService {

    @Value("${s3.bucket}")
    private String bucket;
    @Value("${mcp.accessKeyId}")
    private String accessKeyId;
    @Value("${mcp.secretKey}")
    private String secretKey;
    @Value("${mcp.pipelineName}")
    private String pipelineName;

    private MediaClient mediaClient;

    @Bean
    private MediaClient getMediaClient() {
        if (mediaClient != null) {
            return mediaClient;
        }
        BceClientConfiguration config = new BceClientConfiguration();
        config.setCredentials(new DefaultBceCredentials(accessKeyId, secretKey));
        config.setEndpoint("https://media.bj.baidubce.com");
        // 设置HTTP最大连接数为10
        config.setMaxConnections(10);
        // 设置TCP连接超时为5000毫秒
        config.setConnectionTimeoutInMillis(5000);
        // 设置Socket传输数据超时的时间为2000毫秒
        config.setSocketTimeoutInMillis(2000);
        mediaClient = new MediaClient(config);
        return mediaClient;
    }

    /**
     * 获取视频信息
     *
     * @param key
     * @return
     */
    public GetMediaInfoOfFileResponse getMediaInfo(String key) {
        return getMediaClient().getMediaInfoOfFile(bucket, key);
    }

    /**
     * 创建转码任务
     *
     * @param sourceKey
     * @param targetKey
     * @param resolution
     * @return
     */
    public CreateTranscodingJobResponse createTranscodingJob(
            String sourceKey, String targetKey, String resolution) {
        String presetName;
        if (resolution.equals(Resolution.R_1080P)) {
            presetName = "t_1080p";
        } else if (resolution.equals(Resolution.R_720P)) {
            presetName = "t_720p";
        } else {
            presetName = "t_1080p";
        }
        return getMediaClient().createTranscodingJob(
                pipelineName, sourceKey, targetKey, presetName);
    }

    /**
     * 获取转码任务详情
     *
     * @param jobId
     * @return
     */
    public GetTranscodingJobResponse getTranscodingJob(String jobId) {
        return getMediaClient().getTranscodingJob(jobId);
    }

}
