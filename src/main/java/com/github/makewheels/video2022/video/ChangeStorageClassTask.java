package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.model.OSSObjectSummary;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.utils.PathUtil;
import com.github.makewheels.video2022.video.bean.entity.StorageStatus;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private MongoTemplate mongoTemplate;

    //    @Scheduled(cron = "")
    public void run() {
        List<Video> videos = videoService.getExpiredVideos(0, 5000);
        List<String> videoIds = Lists.transform(videos, Video::getId);
        List<String> videoTitles = Lists.transform(videos, Video::getTitle);

        log.info("删除任务，捞出这些要删除的视频: videoIds = " + JSON.toJSONString(videoIds));
        log.info("删除任务，捞出这些要删除的视频: videoTitles = " + JSON.toJSONString(videoTitles));
        for (Video video : videos) {
            String transcodePath = PathUtil.getS3TranscodePrefix(video.getUploaderId(), video.getId());
            //列举文件
            List<OSSObjectSummary> objects = fileService.findObjects(transcodePath);
            List<String> keys = objects.stream().map(OSSObjectSummary::getKey).collect(Collectors.toList());
            //执行删除
            List<String> deletedKeys = fileService.deleteObjects(keys);
            log.info("删除视频: deletedKeys = {}", JSON.toJSONString(deletedKeys));

            //更新视频状态
            StorageStatus storageStatus = video.getStorageStatus();
            storageStatus.setIsTranscodeFilesDeleted(true);
            storageStatus.setDeleteTime(new Date());
            mongoTemplate.save(video);
        }
    }
}
