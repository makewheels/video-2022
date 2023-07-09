package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.video.service;

import com.aliyun.oss.model.StorageClass;
import com.github.makewheels.video2022.etc.ding.NotificationService;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.videopackage.VideoRepository;
import com.github.makewheels.video2022.videopackage.bean.entity.Video;
import com.github.makewheels.video2022.videopackage.constants.VideoStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 当视频就绪时
 */
@Service
@Slf4j
public class VideoReadyService {
    @Resource
    private EnvironmentService environmentService;
    @Resource
    private NotificationService notificationService;
    @Resource
    private VideoRepository videoRepository;
    @Resource
    private FileService fileService;

    /**
     * 当视频就绪时回调
     */
    public void onVideoReady(String videoId) {
        Video video = videoRepository.getById(videoId);
        log.info("视频已就绪onVideoReady回调, videoId = {}, title = {}", videoId, video.getTitle());

        //发钉钉消息
        sendDing(video);

        // 如果不是link，那就是第一次上传，把OSS视频源文件改为低频存储
        if (!video.getLink().getHasLink()) {
            fileService.changeStorageClass(video.getRawFileId(), StorageClass.IA.toString());
        }
    }

    /**
     * 如果视频已就绪，发送钉钉消息
     */
    private void sendDing(Video video) {
        if (VideoStatus.READY.equals(video.getStatus()) && environmentService.isProductionEnv()) {
            notificationService.sendVideoReadyMessage(video);
        }
    }
}
