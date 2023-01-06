package com.github.makewheels.video2022.startup;

import cn.hutool.system.SystemUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

            JSONObject systemInfo = new JSONObject();
            systemInfo.put("jvmInfo", JSON.parseObject(JSON.toJSONString(SystemUtil.getJvmInfo())));
            systemInfo.put("hostInfo", JSON.parseObject(JSON.toJSONString(SystemUtil.getHostInfo())));
            systemInfo.put("osInfo", JSON.parseObject(JSON.toJSONString(SystemUtil.getOsInfo())));
            systemInfo.put("runtimeInfo", JSON.parseObject(JSON.toJSONString(SystemUtil.getRuntimeInfo())));
            systemInfo.put("userInfo", JSON.parseObject(JSON.toJSONString(SystemUtil.getUserInfo())));

            startupLog.setSystemInfo(systemInfo);

            mongoTemplate.save(startupLog);
        }
    }

}
