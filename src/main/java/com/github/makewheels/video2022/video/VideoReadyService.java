package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 当视频就绪时
 */
@Service
@Slf4j
public class VideoReadyService {
    private VideoRepository videoRepository;

    public void onVideoReady(String videoId) {
        Video video = videoRepository.getById(videoId);
        log.info("视频已就绪, videoId = {}, title = {}", videoId, video.getTitle());
        if (!video.getLink().getHasLink()) {
            // TODO 把原视频改为低频
        }
    }
}
