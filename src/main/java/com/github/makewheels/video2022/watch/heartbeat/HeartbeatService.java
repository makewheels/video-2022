package com.github.makewheels.video2022.watch.heartbeat;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
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

    public Result<Void> add(Heartbeat heartbeat) {
        log.debug("heartbeat: {}", JSON.toJSONString(heartbeat));

        User user = UserHolder.get();
        if (user != null) {
            heartbeat.setViewerId(user.getId());
        }

        heartbeat.setCreateTime(new Date());
        mongoTemplate.save(heartbeat);
        return Result.ok();
    }
}
