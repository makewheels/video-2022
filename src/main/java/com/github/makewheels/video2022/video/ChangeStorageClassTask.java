package com.github.makewheels.video2022.video;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.file.FileService;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

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

        log.info("删除任务，捞出这些要删除的视频: videoIds = " + JSON.toJSONString(videoIds));
        for (Video video : videos) {
            //列举文件
            //执行删除

            //更新视频状态
        }
    }
}
