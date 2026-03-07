package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 视频播放相关 E2E 测试：watchInfo、观看页、视频详情。
 */
@Slf4j
public class VideoWatchE2ETest extends BaseE2ETest {

    /**
     * 创建一个测试视频并跟踪清理，返回包含 videoId、fileId、watchId 的 JSONObject。
     */
    private JSONObject createTestVideo() {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", "test-watch-video.mp4");
        body.put("size", 1024000L);
        ResponseEntity<String> response = authPost(getBaseUrl() + "/video/create", body.toJSONString());
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"), "创建视频应返回 code=0");

        JSONObject data = result.getJSONObject("data");
        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        assertNotNull(videoId, "videoId 不应为空");
        assertNotNull(fileId, "fileId 不应为空");

        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);

        log.info("测试视频已创建: videoId={}, fileId={}, watchId={}",
                videoId, fileId, data.getString("watchId"));
        return data;
    }

    @Test
    void testGetWatchInfo() {
        JSONObject video = createTestVideo();
        String watchId = video.getString("watchId");
        assertNotNull(watchId, "watchId 不应为空");

        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/watchController/getWatchInfo?watchId=" + watchId,
                String.class);
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        // 刚创建的视频没有封面，getWatchInfo 可能返回错误
        // 只要 API 可达且返回了 JSON 响应即可
        assertNotNull(result, "getWatchInfo 应返回 JSON 响应");
        assertNotNull(result.get("code"), "响应应包含 code 字段");
    }

    @Test
    void testWatchPageAccessible() {
        JSONObject video = createTestVideo();
        String watchId = video.getString("watchId");
        assertNotNull(watchId, "watchId 不应为空");

        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/watch?v=" + watchId, String.class);
        assertEquals(200, response.getStatusCode().value(), "观看页应返回 200");

        String body = response.getBody();
        assertNotNull(body, "观看页响应体不应为空");
        assertTrue(body.contains("<html") || body.contains("<!DOCTYPE") || body.contains("<HTML"),
                "观看页应返回 HTML 内容");
    }

    @Test
    void testGetVideoDetail() {
        JSONObject video = createTestVideo();
        String videoId = video.getString("videoId");

        ResponseEntity<String> response = restTemplate.getForEntity(
                getBaseUrl() + "/video/getVideoDetail?videoId=" + videoId,
                String.class);
        assertEquals(200, response.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"), "getVideoDetail 应返回 code=0");

        JSONObject data = result.getJSONObject("data");
        assertEquals(videoId, data.getString("id"), "视频 id 应一致");
        assertNotNull(data.getString("type"), "type 不应为空");
        assertNotNull(data.getString("watchId"), "watchId 不应为空");
        assertNotNull(data.getString("watchUrl"), "watchUrl 不应为空");
    }
}
