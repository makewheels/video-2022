package com.github.makewheels.video2022.statistics;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.statistics.bean.EchartsBar;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

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

    /**
     * Echarts所需数据：按天统计流量消耗
     */
    @GetMapping("/aggregateTrafficData")
    public Result<EchartsBar> aggregateTrafficData(@RequestParam long startTime, @RequestParam long endTime) {
        return statisticsService.aggregateTrafficData(new Date(startTime), new Date(endTime));
    }
}
