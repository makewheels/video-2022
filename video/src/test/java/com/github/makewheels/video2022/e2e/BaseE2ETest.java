package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.oss.service.OssVideoService;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.watch.play.WatchLog;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * E2E 端到端测试基类。
 * 不 mock 任何外部服务，使用本地 dev 数据库和真实阿里云 OSS。
 * 测试后自动清理创建的数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Slf4j
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    protected RestTemplate restTemplate;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected OssVideoService ossVideoService;

    protected final List<String> createdVideoIds = new ArrayList<>();
    protected final List<String> createdFileIds = new ArrayList<>();
    protected final List<String> createdUserIds = new ArrayList<>();
    protected final List<String> createdPlaylistIds = new ArrayList<>();
    protected final List<String> createdOssKeys = new ArrayList<>();

    protected String testToken;
    protected String testUserId;

    private static final String TEST_PHONE = "19900001111";

    @BeforeEach
    void baseSetUp() {
        restTemplate = new RestTemplate();
        login();
    }

    @AfterEach
    void baseCleanup() {
        log.info("E2E 清理开始: videos={}, files={}, users={}, playlists={}, ossKeys={}",
                createdVideoIds.size(), createdFileIds.size(),
                createdUserIds.size(), createdPlaylistIds.size(), createdOssKeys.size());

        for (String key : createdOssKeys) {
            try {
                if (ossVideoService.doesObjectExist(key)) {
                    ossVideoService.deleteObject(key);
                    log.info("已删除 OSS 文件: {}", key);
                }
            } catch (Exception e) {
                log.warn("删除 OSS 文件失败: {}, 原因: {}", key, e.getMessage());
            }
        }

        deleteByIds(Video.class, createdVideoIds);
        deleteByIds(File.class, createdFileIds);
        deleteByIds(Playlist.class, createdPlaylistIds);

        for (String playlistId : createdPlaylistIds) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("playlistId").is(playlistId)),
                    PlayItem.class);
        }

        for (String videoId : createdVideoIds) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("videoId").is(videoId)),
                    WatchLog.class);
        }

        deleteByIds(User.class, createdUserIds);

        createdVideoIds.clear();
        createdFileIds.clear();
        createdUserIds.clear();
        createdPlaylistIds.clear();
        createdOssKeys.clear();

        log.info("E2E 清理完成");
    }

    private <T> void deleteByIds(Class<T> clazz, List<String> ids) {
        for (String id : ids) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), clazz);
        }
    }

    protected void login() {
        String base = getBaseUrl();
        restTemplate.getForEntity(
                base + "/user/requestVerificationCode?phone=" + TEST_PHONE, String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                base + "/user/submitVerificationCode?phone=" + TEST_PHONE + "&code=111",
                String.class);

        JSONObject result = JSONObject.parseObject(response.getBody());
        JSONObject userData = result.getJSONObject("data");
        testToken = userData.getString("token");
        testUserId = userData.getString("id");
        createdUserIds.add(testUserId);

        log.info("E2E 登录成功: userId={}, token={}", testUserId, testToken);
    }

    protected HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    protected ResponseEntity<String> authGet(String url) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
    }

    protected ResponseEntity<String> authPost(String url, Object body) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
}
