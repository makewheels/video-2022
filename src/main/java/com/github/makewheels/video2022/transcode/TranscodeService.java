package com.github.makewheels.video2022.transcode;

import cn.hutool.log.Log;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.*;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailRepository;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.video.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TranscodeService {

    @Value("${s3.bucket}")
    private String bucket;
    @Value("${mcp.accessKeyId}")
    private String accessKeyId;
    @Value("${mcp.secretKey}")
    private String secretKey;
    @Value("${mcp.pipelineName}")
    private String pipelineName;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private ThumbnailRepository thumbnailRepository;

    @Resource
    private VideoService videoService;

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

    /**
     * 处理回调
     * 参考：
     * https://cloud.baidu.com/doc/MCT/s/Hkc9x65yv
     * <p>
     * {
     * "messageId": "bqs-topic-134-1-5",
     * "messageBody": "{\"jobId\":\"job-ffyfa478uh4mjyrz\",\"pipelineName\":\"a2\",\"jobStatus\":\"FAILED\",
     * \"createTime\":\"2015-06-23T05:44:21Z\",\"startTime\":\"2015-06-23T05:44:25Z\",\"endTime\":
     * \"2015-06-23T05:47:36Z\",\"error\":{\"code\":\"JobOverTime\",\"message\":
     * \"thejobhastimedout.pleaseretryit\"},\"source\":{\"sourceKey\":\"1.mp4\"},\"target\":
     * {\"targetKey\":\"out.mp4\",\"presetName\":\"bce.video_mp4_854x480_800kbps\"}}",
     * "subscriptionName": "hello2",
     * "version": "v1alpha",
     * "signature": "BQSsignature"
     * }
     *
     * @param jsonObject
     * @return
     */
    public Result<Void> callback(JSONObject jsonObject) {
        JSONObject messageBody = JSONObject.parseObject(jsonObject.getString("messageBody"));
        String jobId = messageBody.getString("jobId");
        log.info("jobId = " + jobId + "messageBody = ");
        log.info(messageBody.toJSONString());
        //根据jobId查询数据库，首先查询transcode，如果没有再查询thumbnail
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        if (transcode != null) {
            handleTranscodeCallback(transcode);
        } else {
            Thumbnail thumbnail = thumbnailRepository.getByJobId(jobId);
            if (thumbnail != null) {

            }
        }

        return Result.ok();
    }

    /**
     * 处理transcode回调
     *
     * @param transcode
     */
    private void handleTranscodeCallback(Transcode transcode) {
        String jobId = transcode.getJobId();
        GetTranscodingJobResponse response = getTranscodingJob(jobId);
        //更新status
        if (!StringUtils.equals(transcode.getStatus(), response.getJobStatus())) {
            transcode.setStatus(transcode.getStatus());
        }
        //如果已完成，不论成功失败，都保存数据库
        //只有完成状态保存result，pending 和 running不保存result，只保存状态
        if (StringUtils.equals(response.getJobStatus(), TranscodeStatus.FAILED) ||
                StringUtils.equals(response.getJobStatus(), TranscodeStatus.SUCCESS)) {
            transcode.setResult(JSONObject.parseObject(JSON.toJSONString(response)));
        }
        //保存数据库
        mongoTemplate.save(transcode);
        //通知视频转码完成
        videoService.transcodeFinish(transcode);
    }
}
