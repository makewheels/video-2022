package com.github.makewheels.video2022.statistics;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.etc.response.Result;
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
     * <p>
     * {
     * title: {
     * text: '柱状图'
     * },
     * tooltip: {},
     * legend: {
     * data:['销量']
     * },
     * xAxis: {
     * data: ["衬衫","羊毛衫","雪纺衫","裤子","高跟鞋","袜子"]
     * },
     * yAxis: {},
     * series: [{
     * name: '销量',
     * type: 'bar',
     * data: [5, 20, 36, 10, 10, 20],
     * label: {
     * show: true,
     * position: 'top',
     * fontSize: 14,
     * color: '#000'
     * }
     * }]
     * };
     */
    public JSONObject toEchartsBarData(List<Document> documents) {
        List<Document> modifiableDocuments = new ArrayList<>(documents);
        modifiableDocuments.sort((d1, d2) -> {
            String date1 = d1.getString("_id");
            String date2 = d2.getString("_id");
            return date1.compareTo(date2);
        });
        documents = modifiableDocuments;

        JSONObject echartsBar = new JSONObject();

        JSONObject xAxis = new JSONObject();
        JSONArray xAxisData = new JSONArray();

        JSONObject yAxis = new JSONObject();
        JSONArray yAxisData = new JSONArray();

        JSONObject series = new JSONObject();

        JSONArray seriesData = new JSONArray();

        /**
         * { _id: '2023-01-06', totalSize: 179589256 }
         * { _id: '2023-01-14', totalSize: 316371476 }
         * { _id: '2023-01-15', totalSize: 123835600 }
         * { _id: '2023-01-16', totalSize: 49088680 }
         */
        for (Document document : documents) {
            xAxisData.add(document.getString("_id"));
            Long totalSize = document.getLong("totalSize");
            yAxisData.add(FileUtil.readableFileSize(totalSize));
            seriesData.add(totalSize);
        }

        xAxis.put("data", xAxisData);
        xAxis.put("type", "category");

        yAxis.put("type", "value");
        yAxis.put("data", yAxisData);

        series.put("data", seriesData);
        series.put("type", "bar");

        /**
         * label: {
         *     show: true,
         *     position: 'top',
         *     fontSize: 14,
         *     color: '#000'
         * }
         */
        JSONObject label = new JSONObject();
        label.put("show", true);
        label.put("position", "top");
//        label.put("fontSize", 14);
//        label.put("color", "#000");
        series.put("label", label);

        echartsBar.put("xAxis", xAxis);
        echartsBar.put("yAxis", yAxis);
        echartsBar.put("series", series);

        return echartsBar;
    }

    /**
     * Echarts所需数据：按天统计流量消耗
     */
    public Result<JSONObject> aggregateTrafficData(Date startDate, Date endDate) {
        List<Document> documents = statisticsRepository.aggregateTrafficData(startDate, endDate);
        JSONObject echartsBar = toEchartsBarData(documents);
        return Result.ok(echartsBar);
    }

}
