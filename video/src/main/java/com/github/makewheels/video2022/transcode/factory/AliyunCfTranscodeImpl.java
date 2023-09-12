package com.github.makewheels.video2022.transcode.factory;

import cn.hutool.core.util.IdUtil;
import com.github.makewheels.video2022.etc.system.environment.EnvironmentService;
import com.github.makewheels.video2022.transcode.TranscodeCallbackService;
import com.github.makewheels.video2022.transcode.TranscodeRepository;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.cloudfunction.CloudFunctionTranscodeService;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.AudioCodec;
import com.github.makewheels.video2022.video.constants.VideoCodec;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 阿里云CPU云函数转码实现类
 */
@Service
@Slf4j
public class AliyunCfTranscodeImpl implements TranscodeService {
    @Resource
    private EnvironmentService environmentService;

    @Resource
    private CloudFunctionTranscodeService cloudFunctionTranscodeService;
    @Resource
    private TranscodeRepository transcodeRepository;
    @Resource
    private TranscodeCallbackService transcodeCallbackService;
    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public void transcode(Video video, Transcode transcode) {
        String videoId = video.getId();
        String transcodeId = transcode.getId();

        String sourceKey = transcode.getSourceKey();
        String m3u8Key = transcode.getM3u8Key();
        String targetResolution = transcode.getResolution();

        MediaInfo mediaInfo = video.getMediaInfo();
        Integer width = mediaInfo.getWidth();
        Integer height = mediaInfo.getHeight();

        String jobId = IdUtil.getSnowflakeNextIdStr();
        String outputDir = m3u8Key.substring(0, m3u8Key.lastIndexOf("/"));
        String callbackUrl = environmentService.getCallbackUrl(
                "/transcode/aliyunCloudFunctionTranscodeCallback");

        cloudFunctionTranscodeService.transcode(
                sourceKey, outputDir, videoId, transcodeId, jobId, targetResolution, width, height,
                VideoCodec.H264, AudioCodec.AAC, "keep", callbackUrl);

        transcode.setJobId(jobId);
        transcode.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(transcode);
    }

    @Override
    public void callback(String jobId) {
        log.info("开始处理 阿里云 云函数转码完成回调：jobId = " + jobId);
        Transcode transcode = transcodeRepository.getByJobId(jobId);
        transcode.setStatus("FINISHED");
        transcode.setFinishTime(new Date());
        mongoTemplate.save(transcode);
        transcodeCallbackService.onTranscodeFinish(transcode);
    }
}
