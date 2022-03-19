package com.github.makewheels.video2022.thumbnail;

import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.CreateThumbnailJobResponse;
import com.baidubce.services.media.model.GetThumbnailJobResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class ThumbnailService {
    @Resource
    private MediaClient mediaClient;

    @Value("${mcp.pipelineName}")
    private String pipelineName;

    /**
     * 创建抽帧任务
     *
     * @param sourceKey
     * @param targetKeyPrefix
     * @return
     */
    public CreateThumbnailJobResponse createThumbnailJob(String sourceKey, String targetKeyPrefix) {
        return mediaClient.createThumbnailJob(
                pipelineName, "jpg_idl", sourceKey, targetKeyPrefix);

    }

    /**
     * 查询抽帧任务
     *
     * @param jobId
     */
    public GetThumbnailJobResponse getThumbnailJob(String jobId) {
        return mediaClient.getThumbnailJob(jobId);
    }
}
