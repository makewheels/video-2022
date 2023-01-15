package com.github.makewheels.video2022.transcode.factory;

import cn.hutool.core.util.IdUtil;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.video.bean.Video;
import com.github.makewheels.video2022.video.constants.AudioCodec;
import com.github.makewheels.video2022.video.constants.VideoCodec;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 阿里云CPU云函数转码实现类
 */
@Service
@Slf4j
public class AliyunCfTranscodeImpl implements TranscodeService {
    @Value("${external-base-url}")
    private String externalBaseUrl;

    @Resource
    private CloudFunctionTranscodeService cloudFunctionTranscodeService;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;
    @Resource
    private CacheService cacheService;

    @Override
    public Transcode transcode(Video video, Transcode transcode) {
        String videoId = video.getId();
        String transcodeId = transcode.getId();

        String sourceKey = transcode.getSourceKey();
        String m3u8Key = transcode.getM3u8Key();
        String targetResolution = transcode.getResolution();

        Integer width = video.getWidth();
        Integer height = video.getHeight();

        String jobId = IdUtil.getSnowflakeNextIdStr();
        String outputDir = m3u8Key.substring(0, m3u8Key.lastIndexOf("/"));
        String callbackUrl = externalBaseUrl + "/transcode/aliyunCloudFunctionTranscodeCallback";

        cloudFunctionTranscodeService.transcode(
                sourceKey, outputDir, videoId, transcodeId, jobId, targetResolution, width, height,
                VideoCodec.H264, AudioCodec.AAC, "keep", callbackUrl);

        transcode.setJobId(jobId);
        transcode.setStatus(VideoStatus.TRANSCODING);
        cacheService.updateTranscode(transcode);

        return transcode;
    }

    @Override
    public void callback(String jobId) {
        log.info("开始处理 阿里云 云函数转码完成回调：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        transcode.setStatus("FINISHED");
        transcode.setFinishTime(new Date());
        cacheService.updateTranscode(transcode);
        transcodeCallbackService.onTranscodeFinish(transcode);
    }
}
