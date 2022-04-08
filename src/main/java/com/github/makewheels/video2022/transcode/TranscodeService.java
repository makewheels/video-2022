package com.github.makewheels.video2022.transcode;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidubce.BceClientConfiguration;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.media.MediaClient;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.baidubce.services.media.model.GetTranscodingJobResponse;
import com.baidubce.services.media.model.ThumbnailJobStatus;
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
import java.util.Collections;
import java.util.List;

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

    @Value("${cdn-prefetch-url}")
    private String cdnPrefetchUrl;
    @Value("${baseUrl}")
    private String baseUrl;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private ThumbnailRepository thumbnailRepository;

    private MediaClient mediaClient;

    @Resource
    private ThumbnailService thumbnailService;

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
        log.info("处理视频转码回调：jobId = " + jobId + ", messageBody = ");
        log.info(messageBody.toJSONString());
        //根据jobId查询数据库，首先查询transcode，如果没有再查询thumbnail
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        //如果是转码任务
        if (transcode != null) {
            //检查是不是已完成
            if (StringUtils.equals(transcode.getStatus(), TranscodeStatus.SUCCESS)) {
                log.info("转码已完成，跳过");
                return Result.ok();
            }
            handleTranscodeCallback(transcode);
        } else {
            //如果是截帧任务
            Thumbnail thumbnail = thumbnailRepository.getByJobId(jobId);
            if (thumbnail != null) {
                if (StringUtils.equals(thumbnail.getStatus(), TranscodeStatus.SUCCESS)) {
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
        GetTranscodingJobResponse response = getTranscodingJob(jobId);
        //更新status
        transcode.setStatus(response.getJobStatus());
        //如果已完成，不论成功失败，都保存数据库
        //只有完成状态保存result，pending 和 running不保存result，只保存状态
        if (StringUtils.equals(response.getJobStatus(), TranscodeStatus.FAILED)
                || StringUtils.equals(response.getJobStatus(), TranscodeStatus.SUCCESS)) {
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
            if (status.equals(TranscodeStatus.SUCCESS) || status.equals(TranscodeStatus.FAILED)) {
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
        if (!StringUtils.equals(videoStatus, video.getStatus())) {
            video.setStatus(videoStatus);
            mongoTemplate.save(video);
        }
        //当视频已就绪时，也就是所有转码任务都完成了
        if (StringUtils.equals(video.getStatus(), VideoStatus.READY)) {
            onVideoReady(videoId);
        }
    }

    /**
     * 返回最后一个斜杠前的url
     *
     * @param url
     * @return
     */
    private String getBaseUrl(String url) {
        return url.substring(0, url.lastIndexOf("/") + 1);
    }

    /**
     * 当视频已就绪时
     *
     * @param videoId
     */
    public void onVideoReady(String videoId) {
        log.info("视频已就绪, videoId = " + videoId);
        //通知预加载缓存
        JSONObject request = new JSONObject();
        String missionId = IdUtil.getSnowflakeNextIdStr();
        request.put("missionId", missionId);
        List<String> urlList = new ArrayList<>();
        List<Transcode> transcodeList = transcodeRepository.getByVideoId(videoId);
        for (Transcode transcode : transcodeList) {
            String m3u8CdnUrl = transcode.getM3u8CdnUrl();
            String baseUrl = getBaseUrl(m3u8CdnUrl);
            String[] eachLine = HttpUtil.get(m3u8CdnUrl).split("\n");
            for (String line : eachLine) {
                if (line.startsWith("#")) continue;
                urlList.add(baseUrl + line);
            }
        }
        request.put("urlList", urlList);
        request.put("callbackUrl", baseUrl + "/video/onCdnPrefetchFinish");
        log.info("通知预热cdn, missionId = " + missionId + ", size = " + urlList.size());
        String response = HttpUtil.post(cdnPrefetchUrl, request.toJSONString());
        log.info("请求预热，软路由回复：");
        log.info(response);
    }

}
