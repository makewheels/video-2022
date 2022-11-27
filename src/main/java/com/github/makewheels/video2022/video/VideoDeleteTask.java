package com.github.makewheels.video2022.video;

import com.alibaba.fastjson2.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时删除过期视频
 */
@Component
@Slf4j
public class VideoDeleteTask {
    @Resource
    private VideoService videoService;
    @Resource
    private FileService fileService;

    @Resource
    private MongoTemplate mongoTemplate;

    //    @Scheduled(cron = "")
    public void run() {
        List<Video> videos = videoService.getExpiredVideos(0, 5000);
        log.info("删除任务，捞出这些要删除的视频: {}" + JSON.toJSONString(videoService));
        for (Video video : videos) {
            String transcodePath = PathUtil.getS3TranscodePrefix(video.getUserId(), video.getId());
            //列举文件
            List<OSSObjectSummary> objects = fileService.listAllObjects(transcodePath);
            List<String> keys = objects.stream().map(OSSObjectSummary::getKey).collect(Collectors.toList());
            //执行删除
            List<String> deletedKeys = fileService.deleteObjects(keys);
            log.info("删除视频: deletedKeys = {}", JSON.toJSONString(deletedKeys));

            //更新视频状态
            video.setIsTranscodeFilesDeleted(true);
            video.setDeleteTime(new Date());
            mongoTemplate.save(video);
        }
    }
}
