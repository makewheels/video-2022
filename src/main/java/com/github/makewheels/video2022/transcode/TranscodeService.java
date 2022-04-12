package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.QueryJobListResponseBody;
import com.baidubce.services.media.model.GetTranscodingJobResponse;
import com.github.makewheels.video2022.cdn.CdnService;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailRepository;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.video.Provider;
import com.github.makewheels.video2022.video.Video;
import com.github.makewheels.video2022.video.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class TranscodeService {
    @Value("${baidu.bos.bucket}")
    private String bucket;
    @Value("${baidu.mcp.accessKeyId}")
    private String accessKeyId;
    @Value("${baidu.mcp.secretKey}")
    private String secretKey;
    @Value("${baidu.mcp.pipelineName}")
    private String pipelineName;

    @Value("${cdn-prefetch-url}")
    private String cdnPrefetchUrl;
    @Value("${internal-base-url}")
    private String internalBaseUrl;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private ThumbnailRepository thumbnailRepository;

    @Resource
    private ThumbnailService thumbnailService;
    @Resource
    private CdnService cdnService;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private BaiduMcpService baiduMcpService;

    /**
     * 处理百度视频转码回调
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
    public Result<Void> baiduCallback(JSONObject jsonObject) {
        JSONObject messageBody = JSONObject.parseObject(jsonObject.getString("messageBody"));
        String jobId = messageBody.getString("jobId");
        log.info("处理视频转码回调：jobId = " + jobId + ", messageBody = ");
        log.info(messageBody.toJSONString());
        //根据jobId查询数据库，首先查询transcode，如果没有再查询thumbnail
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        //如果是转码任务
        if (transcode != null) {
            //判断是不是已完成
            if (transcode.isFinishStatus()) {
                log.info("百度视频转码已完成，跳过 " + JSON.toJSONString(transcode));
                return Result.ok();
            }
            handleTranscodeCallback(transcode);
        } else {
            //如果是截帧任务
            Thumbnail thumbnail = thumbnailRepository.getByJobId(jobId);
            //判断是不是已完成
            if (thumbnail != null) {
                if (thumbnail.isFinishStatus()) {
                    log.info("百度截帧转码已完成，跳过 " + JSON.toJSONString(thumbnail));
                    return Result.ok();
                }
                thumbnailService.handleThumbnailCallback(thumbnail);
            }
        }
        return Result.ok();
    }

    /**
     * 处理transcode回调
     * 这个处理是通用的，同时兼容百度和阿里，
     * 前面不管是那个服务商，只需根据jobId从数据库查出transcode对象传入即可
     */
    private void handleTranscodeCallback(Transcode transcode) {
        String jobId = transcode.getJobId();
        String jobStatus = null;
        String transcodeResultJson = null;
        //向对应的云服务商查询转码任务
        if (transcode.getProvider().equals(Provider.ALIYUN)) {
            QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                    = aliyunMpsService.queryJob(jobId).getBody().getJobList().getJob().get(0);
            jobStatus = job.getState();
            transcodeResultJson = JSON.toJSONString(job);
        } else if (transcode.getProvider().equals(Provider.BAIDU)) {
            GetTranscodingJobResponse job = baiduMcpService.getTranscodingJob(jobId);
            jobStatus = job.getJobStatus();
            transcodeResultJson = JSON.toJSONString(job);
        }
        //只有在新老状态不一致时，才保存数据库
        if (!StringUtils.equals(jobStatus, transcode.getStatus())) {
            transcode.setStatus(jobStatus);
            transcode.setResult(JSONObject.parseObject(transcodeResultJson));
            mongoTemplate.save(transcode);
            //通知视频转码完成
            transcodeFinish(transcode);
        }
    }

    /**
     * 当有一个转码job完成时回调
     * 主要目的是更新video状态
     *
     * @param transcode
     */
    public void transcodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) return;
        //从数据库中查出，该视频对应的其它的转码任务
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        //统计已完成数量
        int completeCount = (int) transcodeList.stream().filter(Transcode::isFinishStatus).count();
        String videoStatus = null;
        //如果是部分完成
        if (completeCount > 0 && completeCount != transcodeList.size()) {
            videoStatus = VideoStatus.TRANSCODING_PARTLY_COMPLETED;
        } else if (completeCount == transcodeList.size()) {
            //如果全部完成
            videoStatus = VideoStatus.READY;
        } else if (completeCount == 0) {
            //如果一个都没完成，那就是所有任务都在转码
            videoStatus = VideoStatus.TRANSCODING;
        }
        //更新到数据库
        if (!StringUtils.equals(videoStatus, video.getStatus())) {
            video.setStatus(videoStatus);
            mongoTemplate.save(video);
        }
        //当视频已就绪时，也就是所有转码任务都完成了
        if (StringUtils.equals(video.getStatus(), VideoStatus.READY)) {
            onVideoReady(video);
        }
    }

    /**
     * 当视频已就绪时
     */
    public void onVideoReady(Video video) {
        //预热到软路由
        cdnService.softRoutePrefetch(video.getId());
    }

}
