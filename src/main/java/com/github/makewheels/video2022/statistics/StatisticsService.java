package com.github.makewheels.video2022.statistics;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class StatisticsService {
    @Resource
    private StatisticsRepository statisticsRepository;

    public Result<JSONObject> getTrafficConsume(String videoId) {
        long trafficConsume = statisticsRepository.getTrafficConsume(videoId);
        JSONObject response = new JSONObject();
        response.put("videoId", videoId);
        response.put("trafficConsumeInBytes", trafficConsume);
        response.put("trafficConsumeString", FileUtil.readableFileSize(trafficConsume));
        return Result.ok(response);
    }
}
