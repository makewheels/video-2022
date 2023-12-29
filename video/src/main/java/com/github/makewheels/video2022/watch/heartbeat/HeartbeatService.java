package com.github.makewheels.video2022.watch.heartbeat;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.watch.progress.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class HeartbeatService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private ProgressService progressService;

    /**
     * 保存心跳记录
     */
    private void saveHeartbeat(Heartbeat heartbeat) {
        log.debug("heartbeat: {}", JSON.toJSONString(heartbeat));

        User user = UserHolder.get();
        if (user != null) {
            heartbeat.setViewerId(user.getId());
        }

        heartbeat.setCreateTime(new Date());
        mongoTemplate.save(heartbeat);
    }

    /**
     * 接收到客户端心跳
     */
    public Result<Void> add(Heartbeat heartbeat) {
        saveHeartbeat(heartbeat);
        progressService.updateProgress(heartbeat);
        return Result.ok();
    }

}
