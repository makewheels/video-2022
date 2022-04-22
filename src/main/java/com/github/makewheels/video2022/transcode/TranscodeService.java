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
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunTranscodeStatus;
import com.github.makewheels.video2022.transcode.baidu.BaiduMcpService;
import com.github.makewheels.video2022.file.S3Provider;
import com.github.makewheels.video2022.video.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class TranscodeService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private ThumbnailRepository thumbnailRepository;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private BaiduMcpService baiduMcpService;

    @Resource
    private ThumbnailService thumbnailService;
    @Resource
    private CdnService cdnService;

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
    public Result<Void> baiduTranscodeCallback(JSONObject jsonObject) {
        JSONObject messageBody = JSONObject.parseObject(jsonObject.getString("messageBody"));
        String jobId = messageBody.getString("jobId");
        log.info("处理视频转码回调：jobId = " + jobId + ", messageBody = " + messageBody.toJSONString());
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
            if (thumbnail == null) {
                log.info("找不到该jobId，跳过 jobId = " + jobId);
                return Result.ok();
            }
            log.info("百度获取到截帧任务结果：" + JSON.toJSONString(thumbnail));
            if (thumbnail.isFinishStatus()) {
                log.info("百度截帧转码已完成，跳过 " + JSON.toJSONString(thumbnail));
                return Result.ok();
            }
            thumbnailService.handleThumbnailCallback(thumbnail);
        }
        return Result.ok();
    }

    /**
     * 处理阿里云视频转码回调
     */
    public void aliyunTranscodeCallback(String jobId) {
        log.info("阿里云转码回调开始：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        handleTranscodeCallback(transcode);
    }

    /**
     * 因为阿里云的http回调收费，两块钱一个topic，那就一直不停的迭代查询job状态
     */
    public void iterateQueryAliyunTranscodeJob(Video video, Transcode transcode) {
        String jobId = transcode.getJobId();
        Integer duration = video.getDuration();
        long startTime = System.currentTimeMillis();
        //轮询
        for (int i = 0; i < 1000000000; i++) {
            log.info("i = " + i + " 开始睡觉");
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //如果花了视频的5倍时长都没转完，就跳出
            if ((System.currentTimeMillis() - startTime) > 5L * duration) {
                log.error("花了视频的5倍时长都没转完，来人看看这是啥 jobId = {}, video = {}, transcode = {}",
                        jobId, JSON.toJSONString(video), transcode);
                log.error("transcode = " + JSON.toJSONString(transcode));
                break;
            }

            //查询任务
            QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                    = aliyunMpsService.queryJob(jobId).getBody().getJobList().getJob().get(0);
            String jobStatus = job.getState();
            log.debug("阿里云轮询查询job结果: jobStatus = {}, job = {}", jobStatus, JSON.toJSONString(job));
            //如果转码已完成，回调
            if (AliyunTranscodeStatus.isFinishedStatus(jobStatus)) {
                aliyunTranscodeCallback(jobId);
                break;
            }
        }
    }

    /**
     * 处理transcode回调，根据jobId查询百度或阿里接口获取转码情况
     * 这个处理是通用的，同时兼容百度和阿里，
     * 前面不管是那个服务商，只需根据jobId从数据库查出transcode对象传入即可
     */
    private void handleTranscodeCallback(Transcode transcode) {
        String jobId = transcode.getJobId();
        String jobStatus = null;
        String transcodeResultJson = null;
        //向对应的云服务商查询转码任务
        if (transcode.getProvider().equals(S3Provider.ALIYUN_OSS)) {
            QueryJobListResponseBody.QueryJobListResponseBodyJobListJob job
                    = aliyunMpsService.queryJob(jobId).getBody().getJobList().getJob().get(0);
            jobStatus = job.getState();
            transcodeResultJson = JSON.toJSONString(job);
        } else if (transcode.getProvider().equals(S3Provider.BAIDU_BOS)) {
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
            singleTranscodeFinish(transcode);
        }
    }

    /**
     * 当有一个转码job完成时回调
     * 主要目的是：更新video状态
     *
     * @param transcode
     */
    public void singleTranscodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) return;
        //从数据库中查出，该视频对应的所有转码任务
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        //统计已完成数量
        int completeCount = (int) transcodeList.stream().filter(Transcode::isFinishStatus).count();
        String videoStatus;
        //如果是部分完成
        if (completeCount > 0 && completeCount != transcodeList.size()) {
            videoStatus = VideoStatus.TRANSCODING_PARTLY_COMPLETED;
        } else if (completeCount == transcodeList.size()) {
            //如果全部完成
            videoStatus = VideoStatus.READY;
        } else {
            //如果一个都没完成
            videoStatus = VideoStatus.TRANSCODING;
        }
        //更新video到数据库
        if (!StringUtils.equals(videoStatus, video.getStatus())) {
            video.setStatus(videoStatus);
            mongoTemplate.save(video);
        }
        //当所有转码都完成了，也就是视频已就绪时
        if (StringUtils.equals(video.getStatus(), VideoStatus.READY)) {
            onVideoReady(video);
        }
        //判断如果是转码成功状态，请求软路由预热，
        //只有转码成功才预热，失败不预热
        if (transcode.isSuccessStatus()) {
            cdnService.prefetchCdn(transcode);
        }
    }

    /**
     * 当视频已就绪时
     */
    public void onVideoReady(Video video) {
        log.info("视频已就绪 videoId = " + video.getId());
    }

}
