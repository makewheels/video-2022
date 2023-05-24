package com.github.makewheels.video2022.springboot.startup;

import cn.hutool.system.SystemUtil;
import com.alibaba.fastjson.JSON;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
public class StartupService implements ApplicationListener<ContextRefreshedEvent> {
    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            StartupLog startupLog = new StartupLog();
            startupLog.setCreateTime(new Date());

            startupLog.setHostInfo(JSON.parseObject(JSON.toJSONString(SystemUtil.getHostInfo())));
            startupLog.setJvmInfo(JSON.parseObject(JSON.toJSONString(SystemUtil.getJvmInfo())));
            startupLog.setOsInfo(JSON.parseObject(JSON.toJSONString(SystemUtil.getOsInfo())));
            startupLog.setRuntimeInfo(JSON.parseObject(JSON.toJSONString(SystemUtil.getRuntimeInfo())));
            startupLog.setUserInfo(JSON.parseObject(JSON.toJSONString(SystemUtil.getUserInfo())));

            mongoTemplate.save(startupLog);
        }
    }

}
