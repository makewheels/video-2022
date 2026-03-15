package com.github.makewheels.video2022.video.service;

import com.aliyun.oss.model.StorageClass;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.video.VideoRepository;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 当视频就绪时
 */
@Service
@Slf4j
public class VideoReadyService {
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

        // 如果不是link，那就是第一次上传，把OSS视频源文件改为低频存储
        if (!video.getLink().getHasLink()) {
            fileService.changeStorageClass(video.getRawFileId(), StorageClass.IA.toString());
        }
    }
}
