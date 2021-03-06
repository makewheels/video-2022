package com.github.makewheels.video2022.transcode;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitJobsResponseBody;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponseBody;
import com.baidubce.services.media.model.CreateTranscodingJobResponse;
import com.baidubce.services.media.model.GetMediaInfoOfFileResponse;
import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.file.S3Provider;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.baidu.BaiduMcpService;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.AudioCodec;
import com.github.makewheels.video2022.video.constants.VideoCodec;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 发起转码
 */
@Service
@Slf4j
public class TranscodeLauncher {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private TranscodeCallbackService transcodeCallbackService;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private BaiduMcpService baiduMcpService;
    @Resource
    private CloudFunctionTranscodeService cloudFunctionTranscodeService;

    @Value("${external-base-url}")
    private String externalBaseUrl;
    @Value("${baidu.bos.accessBaseUrl}")
    private String baiduBosAccessBaseUrl;
    @Value("${baidu.bos.cdnBaseUrl}")
    private String baiduBosCdnBaseUrl;
    @Value("${aliyun.oss.accessBaseUrl}")
    private String aliyunOssAccessBaseUrl;
    @Value("${aliyun.oss.cdnBaseUrl}")
    private String aliyunOssCdnBaseUrl;

    private boolean isResolutionOverThan720p(int width, int height) {
        return width * height > 1280 * 720;
    }

    private boolean isResolutionOverThanTarget(int width, int height, String resolution) {
        if (resolution.equals(Resolution.R_720P)) {
            return width * height > 1280 * 720;
        } else if (resolution.equals(Resolution.R_1080P)) {
            return width * height > 1920 * 1080;
        }
        return false;
    }

    /**
     * 转码单个分辨率
     */
    private void transcodeSingleResolution(User user, Video video, String resolution) {
        String userId = user.getId();
        String videoId = video.getId();
        String s3Provider = video.getProvider();
        String sourceKey = video.getOriginalFileKey();
        int width = video.getWidth();
        int height = video.getHeight();

        //新建transcode对象，保存到数据库
        Transcode transcode = new Transcode();
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setResolution(resolution);
        transcode.setSourceKey(sourceKey);
        transcode.setCreateTime(new Date());
        //已创建状态，反正后面马上就要再次请求更新状态，所以这里就先保存CREATED
        transcode.setStatus(VideoStatus.CREATED);

        //这里问题来了：如何决定用谁转码？
        //如果不是h264，用阿里云。暂不考虑音频编码，仅音频不是aac也是我来转
        //如果是h264，源视频分辨率和目标分辨率不一致，用阿里云
        //其它情况用自建的阿里云 云函数
        String transcodeProvider;
        if (!VideoCodec.isH264(video.getVideoCodec())) {
            log.info("决定用谁转码：源视频不是h264，用阿里云MPS转码, videoId = " + videoId);
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);
            //关于源视频和转码模板分辨率是否一致，我这样判断：
            //源片分辨率面积小于目标，就一致。大于目标，就不一致
            //说白了就是，往小了转就要编解码，往大了转（当然没这种情况）就源片，所以我云函数不改分辨率，正好
        } else if (isResolutionOverThanTarget(width, height, resolution)) {
            log.info("决定用谁转码：分辨率OverThanTarget，用阿里云MPS转码, videoId = " + videoId);
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);

            //如果码率超标，用阿里云压缩码率
        } else if (video.getBitrate() > 18000) {
            log.info("决定用谁转码：码率超标，用阿里云MPS转码, videoId = " + videoId);
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);

            //如果，是264，分辨率也不用改，但是是百度对象存储，那还得用百度，因为云函数只有阿里云
        } else if (video.getProvider().equals(S3Provider.BAIDU_BOS)) {
            log.info("决定用谁转码：因为文件在百度对象存储，就用百度MCP转码, videoId = " + videoId);
            transcodeProvider = TranscodeProvider.getByS3Provider(s3Provider);
        } else {
            //其它情况用阿里云 云函数
            transcodeProvider = TranscodeProvider.ALIYUN_CLOUD_FUNCTION;
        }
        log.info("transcodeProvider = {}, videoId = {}", transcodeProvider, videoId);
        transcode.setProvider(transcodeProvider);
        mongoTemplate.save(transcode);
        String transcodeId = transcode.getId();

        //设置m3u8 url
        String m3u8Key = PathUtil.getS3VideoPrefix(userId, videoId)
                + "/transcode/" + resolution + "/" + transcodeId + ".m3u8";
        transcode.setM3u8Key(m3u8Key);
        if (s3Provider.equals(S3Provider.ALIYUN_OSS)) {
            transcode.setM3u8AccessUrl(aliyunOssAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(aliyunOssCdnBaseUrl + m3u8Key);
        } else if (s3Provider.equals(S3Provider.BAIDU_BOS)) {
            transcode.setM3u8AccessUrl(baiduBosAccessBaseUrl + m3u8Key);
            transcode.setM3u8CdnUrl(baiduBosCdnBaseUrl + m3u8Key);
        }
        mongoTemplate.save(transcode);

        //发起转码
        log.info("发起 " + resolution + " 转码：videoId = " + videoId + ", transcode-provider = " + transcodeProvider);
        String jobId = null;
        String jobStatus = null;
        switch (transcodeProvider) {
            case TranscodeProvider.ALIYUN_MPS: {
                SubmitJobsResponseBody.SubmitJobsResponseBodyJobResultListJobResultJob job
                        = aliyunMpsService.createTranscodingJobByResolution(sourceKey, m3u8Key, resolution)
                        .getBody().getJobResultList().getJobResult().get(0).getJob();
                jobId = job.getJobId();
                log.info("发起阿里云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
                jobStatus = job.getState();
                break;
            }
            case TranscodeProvider.BAIDU_MCP: {
                CreateTranscodingJobResponse job = baiduMcpService.createTranscodingJob(sourceKey, m3u8Key, resolution);
                jobId = job.getJobId();
                log.info("发起百度云转码 jobId = " + jobId + ", response = " + JSON.toJSONString(job));
                jobStatus = baiduMcpService.getTranscodingJob(jobId).getJobStatus();
                break;
            }
            case TranscodeProvider.ALIYUN_CLOUD_FUNCTION:
                jobId = IdUtil.simpleUUID();
                String callbackUrl = externalBaseUrl + "/transcode/aliyunCloudFunctionTranscodeCallback";
                cloudFunctionTranscodeService.transcode(sourceKey, m3u8Key.substring(0, m3u8Key.lastIndexOf("/")),
                        videoId, transcodeId, jobId, resolution, width, height, VideoCodec.H264, AudioCodec.AAC,
                        "keep", callbackUrl);
                break;
        }
        //保存jobId，更新jobStatus
        transcode.setJobId(jobId);
        transcode.setStatus(jobStatus);
        mongoTemplate.save(transcode);

        //异步轮询查询阿里云转码状态，并回调
        if (transcodeProvider.equals(TranscodeProvider.ALIYUN_MPS)) {
            new Thread(() -> transcodeCallbackService.iterateQueryAliyunTranscodeJob(video, transcode)).start();
        }
    }

    /**
     * 开始发起对单个视频的转码
     */
    public void transcodeVideo(User user, Video video) {
        String videoId = video.getId();

        String sourceKey = video.getOriginalFileKey();
        String videoProvider = video.getProvider();

        //获取视频meta信息
        if (videoProvider.equals(S3Provider.ALIYUN_OSS)) {
            log.info("视频源文件上传完成，通过阿里云获取视频信息，videoId = " + videoId);
            SubmitMediaInfoJobResponseBody body = aliyunMpsService.getMediaInfo(sourceKey).getBody();
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJob job = body.getMediaInfoJob();
            log.info("阿里云获取视频信息返回：" + JSON.toJSONString(job));
            //给video保存媒体信息到数据库
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(job)));
            String jobId = job.getJobId();
            log.info("获取视频信息 jobId = " + jobId);
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobProperties properties
                    = job.getProperties();
            video.setDuration((long) (Double.parseDouble(properties.getDuration()) * 1000));
            video.setHeight(Integer.parseInt(properties.getHeight()));
            video.setWidth(Integer.parseInt(properties.getWidth()));
            video.setBitrate((int) Double.parseDouble(properties.getBitrate()));
            SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreams streams
                    = properties.getStreams();
            video.setVideoCodec(streams.getVideoStreamList().getVideoStream().get(0).getCodecName());
            video.setAudioCodec(streams.getAudioStreamList().getAudioStream().get(0).getCodecName());
        } else if (videoProvider.equals(S3Provider.BAIDU_BOS)) {
            GetMediaInfoOfFileResponse mediaInfo = baiduMcpService.getMediaInfo(sourceKey);
            log.info("视频源文件上传完成，通过百度获取视频信息，videoId = " + videoId + ", mediaInfo = "
                    + JSON.toJSONString(mediaInfo));
            video.setDuration(Long.valueOf(mediaInfo.getDurationInMillisecond()));
            video.setMediaInfo(JSONObject.parseObject(JSON.toJSONString(mediaInfo)));
            video.setWidth(mediaInfo.getVideo().getWidthInPixel());
            video.setHeight(mediaInfo.getVideo().getHeightInPixel());
            video.setVideoCodec(mediaInfo.getVideo().getCodec());
            video.setAudioCodec(mediaInfo.getAudio().getCodec());
            video.setBitrate(mediaInfo.getVideo().getBitRateInBps() + mediaInfo.getAudio().getBitRateInBps());
        }

        //更新数据库video状态
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);

        //发起转码，2022年5月16日22:49:36
        //现在的转码策略改为，只转一个
        if (isResolutionOverThan720p(video.getWidth(), video.getHeight())) {
            transcodeSingleResolution(user, video, Resolution.R_1080P);
        } else {
            transcodeSingleResolution(user, video, Resolution.R_720P);
        }
    }
}
