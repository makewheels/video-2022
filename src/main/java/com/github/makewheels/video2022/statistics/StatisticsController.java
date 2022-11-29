package com.github.makewheels.video2022.statistics;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("statistics")
public class StatisticsController {
    @Resource
    private StatisticsService statisticsService;

    /**
     * 统计指定视频流量消耗
     */
    @GetMapping("getTrafficConsume")
    public Result<JSONObject> getTrafficConsume(@RequestParam String videoId) {
        return statisticsService.getTrafficConsume(videoId);
    }
}
