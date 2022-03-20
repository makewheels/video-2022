package com.github.makewheels.video2022.thumbnail;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.baidubce.services.media.model.GetThumbnailJobResponse;
import com.github.makewheels.video2022.transcode.TranscodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class ThumbnailService {
    private MediaClient mediaClient;

    @Value("${mcp.accessKeyId}")
    private String accessKeyId;
    @Value("${mcp.secretKey}")
    private String secretKey;
    @Value("${mcp.pipelineName}")
    private String pipelineName;

    @Resource
    private MongoTemplate mongoTemplate;

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
     * 创建抽帧任务
     *
     * @param sourceKey
     * @param targetKeyPrefix
     * @return
     */
    public CreateThumbnailJobResponse createThumbnailJob(String sourceKey, String targetKeyPrefix) {
        return getMediaClient().createThumbnailJob(
                pipelineName, "jpg_idl", sourceKey, targetKeyPrefix);

    }

    /**
     * 查询抽帧任务
     *
     * @param jobId
     */
    public GetThumbnailJobResponse getThumbnailJob(String jobId) {
        return getMediaClient().getThumbnailJob(jobId);
    }

    /**
     * 处理thumbnail回调
     *
     * @param thumbnail
     */
    public void handleThumbnailCallback(Thumbnail thumbnail) {
        String jobId = thumbnail.getJobId();
        GetThumbnailJobResponse response = getThumbnailJob(jobId);
        //更新status
        thumbnail.setStatus(response.getJobStatus());
        //如果已完成，不论成功失败，都保存数据库
        //只有完成状态保存result，pending 和 running不保存result，只保存状态
        if (StringUtils.equals(response.getJobStatus(), TranscodeStatus.FAILED) ||
                StringUtils.equals(response.getJobStatus(), TranscodeStatus.SUCCESS)) {
            thumbnail.setResult(JSONObject.parseObject(JSON.toJSONString(response)));
        }
        //保存数据库
        mongoTemplate.save(thumbnail);
        //转码会通知video，截帧就不通知了
    }
}
