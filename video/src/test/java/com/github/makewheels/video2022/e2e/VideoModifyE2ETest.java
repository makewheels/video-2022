package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.video.bean.entity.Video;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 视频信息修改 E2E 测试：更新标题描述、非拥有者修改拒绝。
 */
@Slf4j
public class VideoModifyE2ETest extends BaseE2ETest {

    private JSONObject createTestVideo() {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", "test-modify.mp4");

        ResponseEntity<String> response = authPost(
                getBaseUrl() + "/video/create", body.toJSONString());
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getIntValue("code"), "创建视频应返回 code=0");

        JSONObject data = result.getJSONObject("data");
        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        assertNotNull(videoId, "videoId 不应为空");
        assertNotNull(fileId, "fileId 不应为空");

        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);

        log.info("测试视频已创建: videoId={}, fileId={}", videoId, fileId);
        return data;
    }

    @Test
    void testUpdateVideoInfo() {
        JSONObject video = createTestVideo();
        String videoId = video.getString("videoId");

        String newTitle = "E2E测试标题-修改";
        String newDescription = "E2E测试描述-修改";

        JSONObject updateBody = new JSONObject();
        updateBody.put("id", videoId);
        updateBody.put("title", newTitle);
        updateBody.put("description", newDescription);

        // Update via API
        ResponseEntity<String> updateResponse = authPost(
                getBaseUrl() + "/video/updateInfo", updateBody.toJSONString());
        JSONObject updateResult = JSONObject.parseObject(updateResponse.getBody());
        assertEquals(0, updateResult.getIntValue("code"), "updateInfo 应返回 code=0");

        // Verify API response
        JSONObject updatedData = updateResult.getJSONObject("data");
        assertEquals(newTitle, updatedData.getString("title"), "API 响应的 title 应已更新");
        assertEquals(newDescription, updatedData.getString("description"), "API 响应的 description 应已更新");

        // Verify MongoDB
        Video mongoVideo = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(mongoVideo, "MongoDB 中应存在该视频");
        assertEquals(newTitle, mongoVideo.getTitle(), "MongoDB 中 title 应已更新");
        assertEquals(newDescription, mongoVideo.getDescription(), "MongoDB 中 description 应已更新");

        // Verify getVideoDetail
        ResponseEntity<String> detailResponse = authGet(
                getBaseUrl() + "/video/getVideoDetail?videoId=" + videoId);
        JSONObject detailResult = JSONObject.parseObject(detailResponse.getBody());
        assertEquals(0, detailResult.getIntValue("code"), "getVideoDetail 应返回 code=0");

        JSONObject detailData = detailResult.getJSONObject("data");
        assertEquals(newTitle, detailData.getString("title"), "视频详情的 title 应已更新");
        assertEquals(newDescription, detailData.getString("description"), "视频详情的 description 应已更新");
    }

    @Test
    void testUpdateVideoByOtherUserFails() {
        JSONObject video = createTestVideo();
        String videoId = video.getString("videoId");

        // Login as another user
        String otherPhone = "19900002222";
        restTemplate.getForEntity(
                getBaseUrl() + "/user/requestVerificationCode?phone=" + otherPhone, String.class);
        ResponseEntity<String> loginResponse = restTemplate.getForEntity(
                getBaseUrl() + "/user/submitVerificationCode?phone=" + otherPhone + "&code=111",
                String.class);
        JSONObject loginResult = JSONObject.parseObject(loginResponse.getBody());
        JSONObject otherUser = loginResult.getJSONObject("data");
        String otherToken = otherUser.getString("token");
        String otherUserId = otherUser.getString("id");
        createdUserIds.add(otherUserId);

        // Try to update as non-owner
        JSONObject updateBody = new JSONObject();
        updateBody.put("id", videoId);
        updateBody.put("title", "非法修改");
        updateBody.put("description", "非法修改描述");

        HttpHeaders otherHeaders = new HttpHeaders();
        otherHeaders.set("token", otherToken);
        otherHeaders.set("Content-Type", "application/json");

        ResponseEntity<String> updateResponse = restTemplate.exchange(
                getBaseUrl() + "/video/updateInfo",
                HttpMethod.POST,
                new HttpEntity<>(updateBody.toJSONString(), otherHeaders),
                String.class);

        JSONObject updateResult = JSONObject.parseObject(updateResponse.getBody());
        assertNotEquals(0, updateResult.getIntValue("code"),
                "非视频拥有者不应能修改视频");
        log.info("非拥有者修改视频被正确拒绝, code={}, message={}",
                updateResult.getIntValue("code"), updateResult.getString("message"));
    }
}
