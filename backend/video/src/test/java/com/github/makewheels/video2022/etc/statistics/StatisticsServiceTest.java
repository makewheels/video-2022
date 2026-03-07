package com.github.makewheels.video2022.etc.statistics;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsServiceTest extends BaseIntegrationTest {

    @Autowired
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    // ---- toEchartsBarData tests (pure logic) ----

    @Test
    void toEchartsBarData_emptyList_returnsEmptyArrays() {
        JSONObject result = statisticsService.toEchartsBarData(Collections.emptyList());

        assertNotNull(result);
        JSONObject xAxis = result.getJSONObject("xAxis");
        assertNotNull(xAxis);
        assertEquals("category", xAxis.getString("type"));
        assertTrue(xAxis.getJSONArray("data").isEmpty());

        JSONObject series = result.getJSONObject("series");
        assertNotNull(series);
        assertEquals("bar", series.getString("type"));
        assertTrue(series.getJSONArray("data").isEmpty());
    }

    @Test
    void toEchartsBarData_singleDocument_correctOutput() {
        Document doc = new Document("_id", "2023-06-01").append("totalSize", 1048576L);
        JSONObject result = statisticsService.toEchartsBarData(List.of(doc));

        JSONArray xData = result.getJSONObject("xAxis").getJSONArray("data");
        assertEquals(1, xData.size());
        assertEquals("2023-06-01", xData.getString(0));

        JSONArray seriesData = result.getJSONObject("series").getJSONArray("data");
        assertEquals(1, seriesData.size());
        assertEquals(1048576L, seriesData.getLongValue(0));

        // Label config
        JSONObject label = result.getJSONObject("series").getJSONObject("label");
        assertNotNull(label);
        assertTrue(label.getBooleanValue("show"));
        assertEquals("top", label.getString("position"));
    }

    @Test
    void toEchartsBarData_multipleDocuments_sortedById() {
        List<Document> docs = Arrays.asList(
                new Document("_id", "2023-06-03").append("totalSize", 300L),
                new Document("_id", "2023-06-01").append("totalSize", 100L),
                new Document("_id", "2023-06-02").append("totalSize", 200L)
        );

        JSONObject result = statisticsService.toEchartsBarData(docs);

        JSONArray xData = result.getJSONObject("xAxis").getJSONArray("data");
        assertEquals(3, xData.size());
        // Should be sorted ascending
        assertEquals("2023-06-01", xData.getString(0));
        assertEquals("2023-06-02", xData.getString(1));
        assertEquals("2023-06-03", xData.getString(2));

        JSONArray seriesData = result.getJSONObject("series").getJSONArray("data");
        assertEquals(100L, seriesData.getLongValue(0));
        assertEquals(200L, seriesData.getLongValue(1));
        assertEquals(300L, seriesData.getLongValue(2));
    }

    @Test
    void toEchartsBarData_yAxisContainsReadableSizes() {
        Document doc = new Document("_id", "2023-01-01").append("totalSize", 1073741824L); // 1 GB
        JSONObject result = statisticsService.toEchartsBarData(List.of(doc));

        JSONArray yData = result.getJSONObject("yAxis").getJSONArray("data");
        assertEquals(1, yData.size());
        String readable = yData.getString(0);
        // FileUtil.readableFileSize(1073741824L) → "1 GB" or similar
        assertNotNull(readable);
        assertFalse(readable.isEmpty());
    }

    @Test
    void toEchartsBarData_seriesStructure() {
        Document doc = new Document("_id", "2023-03-15").append("totalSize", 500L);
        JSONObject result = statisticsService.toEchartsBarData(List.of(doc));

        JSONObject series = result.getJSONObject("series");
        assertEquals("bar", series.getString("type"));
        assertNotNull(series.getJSONArray("data"));
        assertNotNull(series.getJSONObject("label"));
    }

    @Test
    void toEchartsBarData_doesNotMutateInput() {
        List<Document> docs = new ArrayList<>(Arrays.asList(
                new Document("_id", "2023-06-02").append("totalSize", 200L),
                new Document("_id", "2023-06-01").append("totalSize", 100L)
        ));
        String firstIdBefore = docs.get(0).getString("_id");

        statisticsService.toEchartsBarData(docs);

        // Original list order should be unchanged
        assertEquals(firstIdBefore, docs.get(0).getString("_id"));
    }

    // ---- getTrafficConsume tests (requires MongoDB data) ----

    @Test
    void getTrafficConsume_withAccessLogs_returnsTotalBytes() {
        String videoId = "v_traffic_001";

        // Insert fileAccessLog documents directly
        Document log1 = new Document("videoId", videoId).append("size", 1000L);
        Document log2 = new Document("videoId", videoId).append("size", 2000L);
        Document log3 = new Document("videoId", videoId).append("size", 3000L);
        mongoTemplate.getCollection("fileAccessLog").insertMany(Arrays.asList(log1, log2, log3));

        Result<JSONObject> result = statisticsService.getTrafficConsume(videoId);

        assertEquals(0, result.getCode());
        JSONObject data = result.getData();
        assertEquals(videoId, data.getString("videoId"));
        assertEquals(6000L, data.getLongValue("trafficConsumeInBytes"));
        assertNotNull(data.getString("trafficConsumeString"));
    }

    // ---- aggregateTrafficData tests (requires MongoDB data) ----

    @Test
    void aggregateTrafficData_groupsByDay() {
        Calendar cal = Calendar.getInstance();
        cal.set(2023, Calendar.JUNE, 1, 10, 0, 0);
        Date day1Morning = cal.getTime();

        cal.set(2023, Calendar.JUNE, 1, 18, 0, 0);
        Date day1Evening = cal.getTime();

        cal.set(2023, Calendar.JUNE, 2, 12, 0, 0);
        Date day2 = cal.getTime();

        Document log1 = new Document("createTime", day1Morning).append("size", 100L);
        Document log2 = new Document("createTime", day1Evening).append("size", 200L);
        Document log3 = new Document("createTime", day2).append("size", 500L);
        mongoTemplate.getCollection("fileAccessLog").insertMany(Arrays.asList(log1, log2, log3));

        cal.set(2023, Calendar.JUNE, 1, 0, 0, 0);
        Date start = cal.getTime();
        cal.set(2023, Calendar.JUNE, 2, 23, 59, 59);
        Date end = cal.getTime();

        Result<JSONObject> result = statisticsService.aggregateTrafficData(start, end);

        assertEquals(0, result.getCode());
        JSONObject data = result.getData();
        assertNotNull(data.getJSONObject("xAxis"));
        assertNotNull(data.getJSONObject("series"));

        JSONArray xData = data.getJSONObject("xAxis").getJSONArray("data");
        // Should have 2 entries (one per day)
        assertEquals(2, xData.size());
    }

    @Test
    void aggregateTrafficData_emptyRange_returnsEmptyChart() {
        Calendar cal = Calendar.getInstance();
        cal.set(2099, Calendar.JANUARY, 1, 0, 0, 0);
        Date start = cal.getTime();
        cal.set(2099, Calendar.JANUARY, 2, 0, 0, 0);
        Date end = cal.getTime();

        Result<JSONObject> result = statisticsService.aggregateTrafficData(start, end);

        assertEquals(0, result.getCode());
        JSONObject data = result.getData();
        JSONArray xData = data.getJSONObject("xAxis").getJSONArray("data");
        assertTrue(xData.isEmpty());
    }
}
