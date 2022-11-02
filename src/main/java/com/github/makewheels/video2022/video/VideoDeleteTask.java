package com.github.makewheels.video2022.video;

import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.Video;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 定时删除过期视频
 */
@Component
public class VideoDeleteTask {
    @Resource
    private VideoService videoService;
    @Resource
    private FileService fileService;

    //    @Scheduled(cron = "")
    public void run() {
        List<Video> videos = videoService.getExpiredVideos(0, 5000);
        for (Video video : videos) {
            String transcodePath = PathUtil.getS3TranscodePrefix(video.getUserId(), video.getId());

        }
    }
}
