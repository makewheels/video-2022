package com.github.makewheels.video2022.transcode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mts20140618.models.SubmitMediaInfoJobResponseBody;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.transcode.aliyun.AliyunMpsService;
import com.github.makewheels.video2022.transcode.bean.Transcode;
import com.github.makewheels.video2022.transcode.contants.Resolution;
import com.github.makewheels.video2022.transcode.contants.TranscodeProvider;
import com.github.makewheels.video2022.transcode.factory.TranscodeFactory;
import com.github.makewheels.video2022.transcode.factory.TranscodeService;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.entity.MediaInfo;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 发起转码
 */
@Service
@Slf4j
public class TranscodeLauncher {
    @Resource
    private EnvironmentService environmentService;

    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private FileService fileService;

    @Resource
    private AliyunMpsService aliyunMpsService;
    @Resource
    private TranscodeFactory transcodeFactory;

    private boolean isResolutionOverThanTarget(int width, int height, String resolution) {
        switch (resolution) {
            case Resolution.R_480P:
                return width * height > 854 * 480;
            case Resolution.R_720P:
                return width * height > 1280 * 720;
            case Resolution.R_1080P:
                return width * height > 1920 * 1080;
        }
        return false;
    }

    /**
     * 决定用谁转码
     */
    private String getTranscodeProvider(Video video, String targetResolution) {
        String videoId = video.getId();
        MediaInfo mediaInfo = video.getMediaInfo();

        //默认用阿里云MPS
        String transcodeProvider = TranscodeProvider.ALIYUN_MPS;
        if (!mediaInfo.getVideoCodec().equals(VideoCodec.H264)) {
            //如果不是h264，用阿里云
            log.info("决定用谁转码：源视频不是h264，用阿里云MPS转码, videoId = " + videoId);

        } else if (isResolutionOverThanTarget(mediaInfo.getWidth(), mediaInfo.getHeight(), targetResolution)) {
            //源视分辨率和目标分辨率不一致，用阿里云
            log.info("决定用谁转码：分辨率OverThanTarget，用阿里云MPS转码, videoId = " + videoId);

        } else if (mediaInfo.getBitrate() > 13000) {
            //如果源片码率太高，用阿里云压缩码率
            log.info("决定用谁转码：码率超标，用阿里云MPS转码, videoId = " + videoId);

        } else {
            //其它情况用阿里云 云函数
            //本地环境都用阿里云mps转码，不用回调。生产环境才用云函数
            if (environmentService.isProductionEnv()) {
                transcodeProvider = TranscodeProvider.ALIYUN_CLOUD_FUNCTION;
            }
        }
        log.info("最终决定用谁转码：transcodeProvider = {}, videoId = {}", transcodeProvider, videoId);
        return transcodeProvider;
    }

    /**
     * 创建新transcode对象
     */
    private Transcode createTranscode(User user, Video video, String targetResolution) {
        String userId = user.getId();
        String videoId = video.getId();

        //新建transcode对象，保存到数据库
        Transcode transcode = new Transcode();
        transcode.setUserId(userId);
        transcode.setVideoId(videoId);
        transcode.setResolution(targetResolution);
        String rawFileKey = fileService.getKey(video.getRawFileId());
        transcode.setSourceKey(rawFileKey);

        //决定用谁转码
        String transcodeProvider = getTranscodeProvider(video, targetResolution);
        transcode.setProvider(transcodeProvider);

        //保存MongoDB，得到id
        mongoTemplate.save(transcode);
        String transcodeId = transcode.getId();

        //设置m3u8 url
        String m3u8Key = PathUtil.getS3VideoPrefix(userId, videoId)
                + "/transcode/" + targetResolution + "/" + transcodeId + ".m3u8";
        transcode.setM3u8Key(m3u8Key);
        mongoTemplate.save(transcode);
        return transcode;
    }

    /**
     * 转码单个分辨率
     */
    private void transcodeSingleResolution(User user, Video video, String targetResolution) {
        String videoId = video.getId();

        //创建新transcode对象
        Transcode transcode = createTranscode(user, video, targetResolution);
        String transcodeId = transcode.getId();

        //反向追加，更新video的transcodeIds
        List<String> transcodeIds = video.getTranscodeIds();
        if (transcodeIds == null) transcodeIds = new ArrayList<>();
        transcodeIds.add(transcodeId);
        video.setTranscodeIds(transcodeIds);

        mongoTemplate.save(video);

        //发起转码
        String transcodeProvider = transcode.getProvider();
        log.info("发起 " + targetResolution + " 转码：videoId = " + videoId + ", transcodeProvider = "
                + transcodeProvider);

        //根据供应商，发起对应转码
        TranscodeService transcodeService = transcodeFactory.getService(transcodeProvider);
        transcodeService.transcode(video, transcode);
    }

    /**
     * 加载媒体信息mediaInfo
     */
    private void loadVideoMediaInfo(Video video) {
        String videoId = video.getId();
        String sourceKey = fileService.getKey(video.getRawFileId());

        log.info("通过阿里云MPS获取视频信息，videoId = {}, title = {}", videoId, video.getTitle());
        //获取视频媒体信息，确定只用阿里云mps，不用其它供应商
        SubmitMediaInfoJobResponseBody body = aliyunMpsService.getMediaInfo(sourceKey).getBody();
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJob job
                = body.getMediaInfoJob();
        log.info("阿里云MPS获取视频媒体信息，jobId ={}，接口返回：{}", job.getJobId(), JSON.toJSONString(job));

        //设置mediaInfo
        MediaInfo mediaInfo = video.getMediaInfo();
        mediaInfo.setResponse(JSONObject.parseObject(JSON.toJSONString(job)));
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobProperties
                properties = job.getProperties();
        mediaInfo.setDuration((long) (Double.parseDouble(properties.getDuration()) * 1000));
        mediaInfo.setHeight(Integer.parseInt(properties.getHeight()));
        mediaInfo.setWidth(Integer.parseInt(properties.getWidth()));
        mediaInfo.setBitrate((int) Double.parseDouble(properties.getBitrate()));
        SubmitMediaInfoJobResponseBody.SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreams
                streams = properties.getStreams();
        mediaInfo.setVideoCodec(streams.getVideoStreamList().getVideoStream().get(0).getCodecName());

        List<SubmitMediaInfoJobResponseBody
                .SubmitMediaInfoJobResponseBodyMediaInfoJobPropertiesStreamsAudioStreamListAudioStream>
                audioStream = streams.getAudioStreamList().getAudioStream();
        // 可能没有音频流，比如无人机拍的视频
        if (CollectionUtils.isNotEmpty(audioStream)) {
            mediaInfo.setAudioCodec(audioStream.get(0).getCodecName());
        }
    }

    /**
     * 开始转码
     */
    public void transcodeVideo(User user, Video video) {
        //加载媒体信息mediaInfo
        loadVideoMediaInfo(video);

        //更新video
        mongoTemplate.save(video);

        //发起转码
        MediaInfo mediaInfo = video.getMediaInfo();
        Integer width = mediaInfo.getWidth();
        Integer height = mediaInfo.getHeight();

        //720p
        if (width * height > 854 * 480) {
            transcodeSingleResolution(user, video, Resolution.R_720P);
        }

        //1080p
        if (width * height > 1280 * 720) {
            transcodeSingleResolution(user, video, Resolution.R_1080P);
        }

    }
}
