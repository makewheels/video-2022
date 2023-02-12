package com.github.makewheels.video2022.statistics;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
import com.github.makewheels.video2022.statistics.bean.EchartsBar;
import com.github.makewheels.video2022.statistics.bean.Series;
import com.github.makewheels.video2022.statistics.bean.XAxis;
import com.github.makewheels.video2022.statistics.bean.YAxis;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class StatisticsService {
    @Resource
    private StatisticsRepository statisticsRepository;

    /**
     * 统计指定videoId流量消耗
     */
    public Result<JSONObject> getTrafficConsume(String videoId) {
        long trafficConsume = statisticsRepository.getTrafficConsume(videoId);
        JSONObject response = new JSONObject();
        response.put("videoId", videoId);
        response.put("trafficConsumeInBytes", trafficConsume);
        response.put("trafficConsumeString", FileUtil.readableFileSize(trafficConsume));
        return Result.ok(response);
    }

    /**
     * 把mongodb转为echarts所需格式
     */
    public static EchartsBar toEchartsBarData(List<Document> documents) {
        EchartsBar echartsBar = new EchartsBar();

        XAxis xAxis = new XAxis();
        List<String> xAxisData = new ArrayList<>();

        YAxis yAxis = new YAxis();
        Series series = new Series();

        List<Long> seriesData = new ArrayList<>();

        /**
         * { _id: '2023-01-06', totalSize: 179589256 }
         * { _id: '2023-01-14', totalSize: 316371476 }
         * { _id: '2023-01-15', totalSize: 123835600 }
         * { _id: '2023-01-16', totalSize: 49088680 }
         */
        for (Document document : documents) {
            xAxisData.add(document.getString("_id"));
            seriesData.add(document.getLong("totalSize"));
        }

        xAxis.setData(xAxisData);
        xAxis.setType("category");

        yAxis.setType("value");
        series.setData(seriesData);
        series.setType("bar");

        echartsBar.setXAxis(xAxis);
        echartsBar.setYAxis(yAxis);
        echartsBar.setSeries(Lists.newArrayList(series));

        return echartsBar;
    }

    /**
     * Echarts所需数据：按天统计流量消耗
     */
    public Result<EchartsBar> aggregateTrafficData(Date startDate, Date endDate) {
        List<Document> documents = statisticsRepository.aggregateTrafficData(startDate, endDate);
        EchartsBar echartsBar = toEchartsBarData(documents);
        return Result.ok(echartsBar);
    }

}
