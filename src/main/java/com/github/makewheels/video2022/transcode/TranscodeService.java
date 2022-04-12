package com.github.makewheels.video2022.transcode;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.baidubce.services.media.model.GetTranscodingJobResponse;
import com.github.makewheels.video2022.cdn.CdnService;
import com.github.makewheels.video2022.response.Result;
import com.github.makewheels.video2022.thumbnail.Thumbnail;
import com.github.makewheels.video2022.thumbnail.ThumbnailRepository;
import com.github.makewheels.video2022.thumbnail.ThumbnailService;
import com.github.makewheels.video2022.video.Video;
import com.github.makewheels.video2022.video.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
    public Result<Void> baiduCallback(JSONObject jsonObject) {
        JSONObject messageBody = JSONObject.parseObject(jsonObject.getString("messageBody"));
        String jobId = messageBody.getString("jobId");
        log.info("处理视频转码回调：jobId = " + jobId + ", messageBody = ");
        log.info(messageBody.toJSONString());
        //根据jobId查询数据库，首先查询transcode，如果没有再查询thumbnail
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        //如果是转码任务
        if (transcode != null) {
            //检查是不是已完成
            if (StringUtils.equals(transcode.getStatus(), BaiduTranscodeStatus.SUCCESS)) {
                log.info("转码已完成，跳过");
                return Result.ok();
            }
            handleTranscodeCallback(transcode);
        } else {
            //如果是截帧任务
            Thumbnail thumbnail = thumbnailRepository.getByJobId(jobId);
            if (thumbnail != null) {
                if (StringUtils.equals(thumbnail.getStatus(), BaiduTranscodeStatus.SUCCESS)) {
                    log.info("转码已完成，跳过");
                    return Result.ok();
                }
                thumbnailService.handleThumbnailCallback(thumbnail);
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
        GetTranscodingJobResponse response = baiduMcpService.getTranscodingJob(jobId);
        //更新status
        transcode.setStatus(response.getJobStatus());
        //如果已完成，不论成功失败，都保存数据库
        //只有完成状态保存result，pending 和 running不保存result，只保存状态
        if (StringUtils.equals(response.getJobStatus(), BaiduTranscodeStatus.FAILED)
                || StringUtils.equals(response.getJobStatus(), BaiduTranscodeStatus.SUCCESS)) {
            transcode.setResult(JSONObject.parseObject(JSON.toJSONString(response)));
        }
        //保存数据库
        mongoTemplate.save(transcode);
        //通知视频转码完成
        transcodeFinish(transcode);
    }

    /**
     * 当有一个转码job完成时回调
     *
     * @param transcode
     */
    public void transcodeFinish(Transcode transcode) {
        String videoId = transcode.getVideoId();
        Video video = mongoTemplate.findById(videoId, Video.class);
        if (video == null) return;
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        int completeCount = 0;
        //统计已完成数量
        for (Transcode eachTranscode : transcodeList) {
            String status = eachTranscode.getStatus();
            if (status.equals(BaiduTranscodeStatus.SUCCESS) || status.equals(BaiduTranscodeStatus.FAILED)) {
                completeCount++;
            }
        }
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
