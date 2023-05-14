package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.redis.CacheService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.video.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 改低频存储
 */
@Component
@Slf4j
public class ChangeStorageClassTask {
    @Resource
    private VideoService videoService;
    @Resource
    private FileService fileService;
    @Resource
    private CacheService cacheService;

    //    @Scheduled(cron = "")
    public void run() {
        List<Video> videos = videoService.getExpiredVideos(0, 5000);
        log.info("删除任务，捞出这些要删除的视频: {}" + JSON.toJSONString(videoService));
        for (Video video : videos) {
            String transcodePath = PathUtil.getS3TranscodePrefix(video.getUserId(), video.getId());
            //列举文件
            List<OSSObjectSummary> objects = fileService.findObjects(transcodePath);
            List<String> keys = objects.stream().map(OSSObjectSummary::getKey).collect(Collectors.toList());
            //执行删除
            List<String> deletedKeys = fileService.deleteObjects(keys);
            log.info("删除视频: deletedKeys = {}", JSON.toJSONString(deletedKeys));

            //更新视频状态
            video.setIsTranscodeFilesDeleted(true);
            video.setDeleteTime(new Date());
            cacheService.updateVideo(video);
        }
    }
}
