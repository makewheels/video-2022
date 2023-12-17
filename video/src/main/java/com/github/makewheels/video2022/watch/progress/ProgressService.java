package com.github.makewheels.video2022.watch.progress;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.watch.heartbeat.Heartbeat;
import com.github.makewheels.video2022.watch.heartbeat.PlayerStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class ProgressService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private ProgressRepository progressRepository;

    /**
     * 更新视频播放记忆进度
     */
    public void updateProgress(Heartbeat heartbeat) {
        if (!PlayerStatus.PLAYING.equals(heartbeat.getPlayerStatus())) {
            return;
        }
        Progress progress = progressRepository.getProgress(
                heartbeat.getVideoId(), heartbeat.getViewerId(), heartbeat.getClientId());
        if (progress == null) {
            progress = new Progress();
            progress.setVideoId(heartbeat.getVideoId());
            progress.setViewerId(heartbeat.getViewerId());
            progress.setClientId(heartbeat.getClientId());
            log.info("视频播放进度不存在，创建新的进度记录");
        } else {
            progress.setUpdateTime(new Date());
            log.info("视频播放进度已存在，更新进度记录");
        }
        progress.setLastSessionId(heartbeat.getSessionId());
        progress.setProgressInMillis(heartbeat.getPlayerTime());

        log.info("保存视频播放进度: " + JSON.toJSONString(progress));
        mongoTemplate.save(progress);
    }

    /**
     * 获取视频播放进度
     */
    public Progress getProgress(String videoId, String viewerId, String clientId) {
        return progressRepository.getProgress(videoId, viewerId, clientId);
    }

}
