> **状态:** ✅ 已完成 — [PR #16](https://github.com/makewheels/video-2022/pull/16)

# E2E 端到端测试 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 建立真实的端到端测试体系，覆盖登录、视频上传、修改、播放、播放列表 5 大场景，验证 MongoDB + OSS + 网页 三端数据一致性。

**Architecture:** 新建 `BaseE2ETest` 基类（不 mock 任何服务），使用 dev 配置连接本地 MongoDB（`video-2022`）和真实阿里云 OSS（`video-2022-dev`）。通过 `TestRestTemplate` 发送真实 HTTP 请求。Playwright 浏览器 E2E 测试验证 UI 交互。

**Tech Stack:** JUnit 5, Spring Boot Test, TestRestTemplate, Aliyun OSS SDK, Playwright (Node.js), FFmpeg (生成测试视频)

**关键约束:**
- E2E 测试需要 `.env` 中的阿里云凭证，运行前需 export 环境变量
- 测试使用 dev 数据库 `video-2022`，测试后必须清理数据
- 测试后删除 OSS 上的测试文件
- dev 模式验证码 `111` 万能通过
- 认证方式：请求头 `token` 或 URL 参数 `?token=xxx`

---

### Task 1: 创建分支和 E2E 配置文件

**Files:**
- Create: `video/src/test/resources/application-e2e.properties`

**Step 1: 创建分支**

```bash
cd /Users/mint/java-projects/video-2022
git checkout master && git pull origin master
git checkout -b feat/e2e-tests
```

**Step 2: 创建 E2E profile 配置**

Create `video/src/test/resources/application-e2e.properties`:

```properties
# E2E 测试配置 - 使用本地 dev 数据库和真实阿里云 OSS
server.port=0

spring.thymeleaf.cache=false
logging.level.org.springframework.data.convert.CustomConversions=ERROR

# 使用本地 dev 数据库（不是 test 库）
spring.mongodb.host=localhost
spring.mongodb.port=27017
spring.mongodb.database=video-2022
spring.data.mongodb.auto-index-creation=true

spring.redis.host=localhost
spring.redis.port=6379

internal-base-url=http://localhost:${local.server.port}
external-base-url=http://localhost:${local.server.port}

short-url-service=http://localhost:5027/add
youtube-service-url=https://video-youtube-video-hongkong-iabsngmqbv.cn-hongkong.fcapp.run

# 真实 OSS 配置（从环境变量读取）
aliyun.oss.video.bucket=video-2022-dev
aliyun.oss.video.endpoint=oss-cn-beijing.aliyuncs.com
aliyun.oss.video.internal-endpoint=oss-cn-beijing-internal.aliyuncs.com
aliyun.oss.video.accessKeyId=${ALIYUN_OSS_VIDEO_ACCESS_KEY_ID:}
aliyun.oss.video.secretKey=${ALIYUN_OSS_VIDEO_SECRET_KEY:}
aliyun.oss.video.accessBaseUrl=https://video-2022-dev.oss-cn-beijing.aliyuncs.com/

aliyun.oss.data.bucket=oss-data-bucket
aliyun.oss.data.endpoint=oss-cn-beijing.aliyuncs.com
aliyun.oss.data.internal-endpoint=oss-cn-beijing-internal.aliyuncs.com
aliyun.oss.data.accessKeyId=${ALIYUN_OSS_DATA_ACCESS_KEY_ID:}
aliyun.oss.data.secretKey=${ALIYUN_OSS_DATA_SECRET_KEY:}
aliyun.oss.data.inventory-prefix=video-2022-dev/inventory/video-2022-dev/inventory-rule
aliyun.oss.data.accesslog-prefix=video-2022-dev/accesslog

aliyun.mps.accessKeyId=${ALIYUN_MPS_ACCESS_KEY_ID:}
aliyun.mps.secretKey=${ALIYUN_MPS_SECRET_KEY:}

aliyun.apigateway.ip.appcode=${ALIYUN_APIGATEWAY_IP_APPCODE:}

aliyun.cf.video-2022.get-oss-object-md5.url=https://get-ossbject-md-video-dev-mcamscgqnw.cn-beijing.fcapp.run
```

**Step 3: 提交**

```bash
git add video/src/test/resources/application-e2e.properties
git commit -m "feat: 添加E2E测试配置文件

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: 创建 BaseE2ETest 基类

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/BaseE2ETest.java`

**Step 1: 编写 BaseE2ETest**

```java
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

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

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected OssVideoService ossVideoService;

    // 记录测试中创建的资源，用于清理
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
        // 每次测试前先登录获取 token
        login();
    }

    @AfterEach
    void baseCleanup() {
        log.info("E2E 清理开始: videos={}, files={}, users={}, playlists={}, ossKeys={}",
                createdVideoIds.size(), createdFileIds.size(),
                createdUserIds.size(), createdPlaylistIds.size(), createdOssKeys.size());

        // 删除 OSS 文件
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

        // 删除 MongoDB 数据
        deleteByIds(Video.class, createdVideoIds);
        deleteByIds(File.class, createdFileIds);
        deleteByIds(Playlist.class, createdPlaylistIds);

        // 删除 PlayItem（通过 playlistId 关联删除）
        for (String playlistId : createdPlaylistIds) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("playlistId").is(playlistId)),
                    PlayItem.class);
        }

        // 删除 WatchLog（通过 videoId 关联删除）
        for (String videoId : createdVideoIds) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("videoId").is(videoId)),
                    WatchLog.class);
        }

        // 删除测试用户
        deleteByIds(User.class, createdUserIds);

        // 清空列表
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

    /**
     * 登录获取 token（使用 dev 模式万能验证码 111）
     */
    protected void login() {
        // 请求验证码
        restTemplate.getForEntity(
                "/user/requestVerificationCode?phone=" + TEST_PHONE, String.class);

        // 提交验证码（dev 模式 111 万能通过）
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/user/submitVerificationCode?phone=" + TEST_PHONE + "&code=111",
                String.class);

        JSONObject result = JSONObject.parseObject(response.getBody());
        JSONObject userData = result.getJSONObject("data");
        testToken = userData.getString("token");
        testUserId = userData.getString("id");
        createdUserIds.add(testUserId);

        log.info("E2E 登录成功: userId={}, token={}", testUserId, testToken);
    }

    /**
     * 创建带 token 认证的 HTTP 请求头
     */
    protected HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * 发送带认证的 GET 请求
     */
    protected ResponseEntity<String> authGet(String url) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
    }

    /**
     * 发送带认证的 POST 请求
     */
    protected ResponseEntity<String> authPost(String url, Object body) {
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), String.class);
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
}
```

**Step 2: 验证编译通过**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn compile -pl video -Pspringboot -Dmaven.test.skip=true -q
```

**Step 3: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/BaseE2ETest.java
git commit -m "feat: 创建E2E测试基类BaseE2ETest

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: 登录注册 E2E 测试

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/LoginE2ETest.java`

**Step 1: 编写测试**

```java
package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.user.bean.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: 登录注册流程
 * - 请求验证码 → 提交验证码 → 获取 token
 * - 用 token 访问受保护接口
 * - token 错误时访问受保护接口返回 403
 */
class LoginE2ETest extends BaseE2ETest {

    @Test
    void testLoginFlowAndTokenValidation() {
        // baseSetUp 已经完成了登录，验证 token 和 userId 存在
        assertNotNull(testToken, "登录后应获得 token");
        assertNotNull(testUserId, "登录后应获得 userId");

        // 验证 MongoDB 中有用户记录
        User user = mongoTemplate.findById(testUserId, User.class);
        assertNotNull(user, "MongoDB 中应存在用户记录");
        assertEquals("19900001111", user.getPhone(), "手机号应正确");
        assertEquals(testToken, user.getToken(), "token 应匹配");

        // 用 token 访问受保护接口 - 应成功
        ResponseEntity<String> protectedResponse = authGet(
                "/video/getMyVideoList?skip=0&limit=10");
        assertEquals(200, protectedResponse.getStatusCode().value(),
                "有效 token 应返回 200");

        JSONObject result = JSONObject.parseObject(protectedResponse.getBody());
        assertEquals(0, result.getInteger("code"), "业务状态码应为成功");
    }

    @Test
    void testInvalidTokenReturns403() {
        // 使用错误的 token 访问受保护接口
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("token", "invalid-token-12345");
        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/video/getMyVideoList?skip=0&limit=10",
                org.springframework.http.HttpMethod.GET, entity, String.class);

        // CheckTokenInterceptor 应返回 403 或重定向
        assertTrue(response.getStatusCode().value() == 403
                        || response.getStatusCode().value() == 302,
                "无效 token 应返回 403 或 302 重定向");
    }

    @Test
    void testGetUserByToken() {
        // 通过 token 获取用户信息
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/user/getUserByToken?token=" + testToken, String.class);

        assertEquals(200, response.getStatusCode().value());
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertEquals(0, result.getInteger("code"));

        JSONObject userData = result.getJSONObject("data");
        assertEquals(testUserId, userData.getString("id"));
    }

    @Test
    void testReLoginRefreshesToken() {
        String firstToken = testToken;

        // 重新登录（同一手机号）
        restTemplate.getForEntity(
                "/user/requestVerificationCode?phone=19900001111", String.class);
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/user/submitVerificationCode?phone=19900001111&code=111", String.class);

        JSONObject result = JSONObject.parseObject(response.getBody());
        String newToken = result.getJSONObject("data").getString("token");

        // 新 token 应不同于旧 token
        assertNotEquals(firstToken, newToken, "重新登录应生成新 token");

        // 更新 testToken
        testToken = newToken;
    }
}
```

**Step 2: 运行测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest=com.github.makewheels.video2022.e2e.LoginE2ETest \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

Expected: All 4 tests PASS

**Step 3: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/LoginE2ETest.java
git commit -m "test: 登录注册E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 4: 视频创建上传 E2E 测试

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/VideoUploadE2ETest.java`
- Create: `video/src/test/resources/e2e/test-video.mp4` (小测试视频文件)

**Step 1: 生成测试视频文件**

```bash
mkdir -p video/src/test/resources/e2e
ffmpeg -y -f lavfi -i testsrc=duration=1:size=320x240:rate=1 \
  -c:v libx264 -pix_fmt yuv420p \
  video/src/test/resources/e2e/test-video.mp4
```

如果没有 ffmpeg，用 Java 代码在测试中动态生成一个小文件。

**Step 2: 编写测试**

```java
package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: 视频创建上传全流程
 * - 创建视频 → 获取上传凭证 → 上传文件到 OSS → 通知上传完成
 * - 验证 MongoDB 中 Video 和 File 状态
 * - 验证 OSS 中文件存在
 */
class VideoUploadE2ETest extends BaseE2ETest {

    @Test
    void testCreateAndUploadVideo() {
        // ── Step 1: 创建视频 ──
        JSONObject createBody = new JSONObject();
        createBody.put("videoType", "USER_UPLOAD");
        createBody.put("rawFilename", "e2e-test-video.mp4");

        ResponseEntity<String> createResponse = authPost("/video/create", createBody.toJSONString());
        assertEquals(200, createResponse.getStatusCode().value(), "创建视频应返回 200");

        JSONObject createResult = JSONObject.parseObject(createResponse.getBody());
        assertEquals(0, createResult.getInteger("code"), "创建视频业务状态码应为 0");

        JSONObject createData = createResult.getJSONObject("data");
        String videoId = createData.getString("videoId");
        String fileId = createData.getString("fileId");
        String watchId = createData.getString("watchId");

        assertNotNull(videoId, "应返回 videoId");
        assertNotNull(fileId, "应返回 fileId");
        assertNotNull(watchId, "应返回 watchId");
        assertTrue(videoId.startsWith("v_"), "videoId 应以 v_ 开头");
        assertTrue(fileId.startsWith("f_"), "fileId 应以 f_ 开头");

        // 记录以便清理
        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);

        // ── Step 2: 验证 MongoDB 中的 Video 记录 ──
        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video, "MongoDB 中应有 Video 记录");
        assertEquals(testUserId, video.getUploaderId(), "上传者 ID 应匹配");
        assertEquals("USER_UPLOAD", video.getVideoType(), "视频类型应为 USER_UPLOAD");
        assertNotNull(video.getWatch(), "Watch 信息应存在");
        assertEquals(watchId, video.getWatch().getWatchId(), "watchId 应匹配");

        // ── Step 3: 验证 MongoDB 中的 File 记录 ──
        File file = mongoTemplate.findById(fileId, File.class);
        assertNotNull(file, "MongoDB 中应有 File 记录");
        assertEquals(videoId, file.getVideoId(), "File 的 videoId 应匹配");
        assertEquals("mp4", file.getExtension(), "文件扩展名应为 mp4");
        assertNotNull(file.getKey(), "OSS key 应已生成");

        // 记录 OSS key 以便清理
        createdOssKeys.add(file.getKey());

        // ── Step 4: 获取上传凭证 ──
        ResponseEntity<String> credResponse = authGet(
                "/file/getUploadCredentials?fileId=" + fileId);
        assertEquals(200, credResponse.getStatusCode().value(), "获取上传凭证应返回 200");

        JSONObject credResult = JSONObject.parseObject(credResponse.getBody());
        JSONObject credentials = credResult.getJSONObject("data");

        String bucket = credentials.getString("bucket");
        String key = credentials.getString("key");
        String endpoint = credentials.getString("endpoint");
        String stsAccessKeyId = credentials.getString("accessKeyId");
        String stsSecretKey = credentials.getString("secretKey");
        String stsSessionToken = credentials.getString("sessionToken");

        assertNotNull(stsAccessKeyId, "STS accessKeyId 应存在");
        assertNotNull(stsSecretKey, "STS secretKey 应存在");
        assertNotNull(stsSessionToken, "STS sessionToken 应存在");
        assertEquals(file.getKey(), key, "凭证中的 key 应与 File 的 key 一致");

        // ── Step 5: 使用 STS 凭证上传文件到 OSS ──
        byte[] testFileContent = "E2E test video content - not a real video".getBytes();
        OSS stsClient = new OSSClientBuilder().build(
                "https://" + endpoint, stsAccessKeyId, stsSecretKey, stsSessionToken);
        try {
            stsClient.putObject(bucket, key, new ByteArrayInputStream(testFileContent));
        } finally {
            stsClient.shutdown();
        }

        // ── Step 6: 验证 OSS 中文件存在 ──
        assertTrue(ossVideoService.doesObjectExist(key), "OSS 中应存在上传的文件");

        // ── Step 7: 通知文件上传完成 ──
        ResponseEntity<String> finishResponse = authGet(
                "/file/uploadFinish?fileId=" + fileId);
        assertEquals(200, finishResponse.getStatusCode().value(), "通知上传完成应返回 200");

        // ── Step 8: 验证 File 状态更新 ──
        File updatedFile = mongoTemplate.findById(fileId, File.class);
        assertNotNull(updatedFile);
        assertEquals("READY", updatedFile.getFileStatus(), "文件状态应为 READY");
        assertNotNull(updatedFile.getSize(), "文件大小应已填充");
        assertNotNull(updatedFile.getEtag(), "ETag 应已填充");
        assertNotNull(updatedFile.getUploadTime(), "上传时间应已填充");
    }

    @Test
    void testCreateVideoAppearsInMyVideoList() {
        // 创建视频
        JSONObject createBody = new JSONObject();
        createBody.put("videoType", "USER_UPLOAD");
        createBody.put("rawFilename", "e2e-list-test.mp4");

        ResponseEntity<String> createResponse = authPost("/video/create", createBody.toJSONString());
        JSONObject createData = JSONObject.parseObject(createResponse.getBody()).getJSONObject("data");
        String videoId = createData.getString("videoId");
        String fileId = createData.getString("fileId");
        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);

        // 查询我的视频列表
        ResponseEntity<String> listResponse = authGet(
                "/video/getMyVideoList?skip=0&limit=100");
        assertEquals(200, listResponse.getStatusCode().value());

        JSONObject listResult = JSONObject.parseObject(listResponse.getBody());
        assertEquals(0, listResult.getInteger("code"));

        // 验证列表中包含刚创建的视频
        boolean found = false;
        for (Object item : listResult.getJSONArray("data")) {
            JSONObject videoVO = (JSONObject) item;
            if (videoId.equals(videoVO.getString("id"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "我的视频列表中应包含刚创建的视频");
    }
}
```

**Step 3: 运行测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest=com.github.makewheels.video2022.e2e.VideoUploadE2ETest \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

Expected: All tests PASS

**Step 4: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/VideoUploadE2ETest.java
git add video/src/test/resources/e2e/ 2>/dev/null
git commit -m "test: 视频创建上传E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 5: 视频信息修改 E2E 测试

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/VideoModifyE2ETest.java`

**Step 1: 编写测试**

```java
package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.video.bean.entity.Video;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: 视频信息修改
 * - 创建视频 → 修改标题和描述 → 验证 DB 和 API 返回新值
 */
class VideoModifyE2ETest extends BaseE2ETest {

    private String createTestVideo() {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", "e2e-modify-test.mp4");
        ResponseEntity<String> response = authPost("/video/create", body.toJSONString());
        JSONObject data = JSONObject.parseObject(response.getBody()).getJSONObject("data");
        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);
        return videoId;
    }

    @Test
    void testUpdateVideoInfo() {
        String videoId = createTestVideo();

        // 修改标题和描述
        JSONObject updateBody = new JSONObject();
        updateBody.put("id", videoId);
        updateBody.put("title", "E2E测试标题-修改后");
        updateBody.put("description", "E2E测试描述-修改后的详细描述");

        ResponseEntity<String> updateResponse = authPost("/video/updateInfo", updateBody.toJSONString());
        assertEquals(200, updateResponse.getStatusCode().value(), "修改视频信息应返回 200");

        JSONObject updateResult = JSONObject.parseObject(updateResponse.getBody());
        assertEquals(0, updateResult.getInteger("code"), "业务状态码应为 0");

        // 验证 API 返回的数据包含新值
        JSONObject updatedData = updateResult.getJSONObject("data");
        assertEquals("E2E测试标题-修改后", updatedData.getString("title"));
        assertEquals("E2E测试描述-修改后的详细描述", updatedData.getString("description"));

        // 直接查 MongoDB 验证
        Video video = mongoTemplate.findById(videoId, Video.class);
        assertNotNull(video);
        assertEquals("E2E测试标题-修改后", video.getTitle(), "MongoDB 中 title 应已更新");
        assertEquals("E2E测试描述-修改后的详细描述", video.getDescription(), "MongoDB 中 description 应已更新");

        // 通过 getVideoDetail API 再次验证
        ResponseEntity<String> detailResponse = restTemplate.getForEntity(
                "/video/getVideoDetail?videoId=" + videoId, String.class);
        assertEquals(200, detailResponse.getStatusCode().value());
        JSONObject detailData = JSONObject.parseObject(detailResponse.getBody()).getJSONObject("data");
        assertEquals("E2E测试标题-修改后", detailData.getString("title"), "API 返回的 title 应是新值");
        assertEquals("E2E测试描述-修改后的详细描述", detailData.getString("description"),
                "API 返回的 description 应是新值");
    }

    @Test
    void testUpdateVideoByOtherUserFails() {
        String videoId = createTestVideo();

        // 用另一个手机号登录（模拟其他用户）
        restTemplate.getForEntity(
                "/user/requestVerificationCode?phone=19900002222", String.class);
        ResponseEntity<String> loginResp = restTemplate.getForEntity(
                "/user/submitVerificationCode?phone=19900002222&code=111", String.class);
        JSONObject otherUser = JSONObject.parseObject(loginResp.getBody()).getJSONObject("data");
        String otherToken = otherUser.getString("token");
        String otherUserId = otherUser.getString("id");
        createdUserIds.add(otherUserId);

        // 用其他用户的 token 尝试修改
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("token", otherToken);
        headers.set("Content-Type", "application/json");

        JSONObject updateBody = new JSONObject();
        updateBody.put("id", videoId);
        updateBody.put("title", "被其他用户篡改");

        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(updateBody.toJSONString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/video/updateInfo", org.springframework.http.HttpMethod.POST,
                entity, String.class);

        // 应返回错误（非视频所有者）
        JSONObject result = JSONObject.parseObject(response.getBody());
        assertNotEquals(0, result.getInteger("code"), "非所有者修改应返回错误码");
    }
}
```

**Step 2: 运行测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest=com.github.makewheels.video2022.e2e.VideoModifyE2ETest \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

Expected: All 2 tests PASS

**Step 3: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/VideoModifyE2ETest.java
git commit -m "test: 视频信息修改E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 6: 视频播放验证 E2E 测试

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/VideoWatchE2ETest.java`

**Step 1: 编写测试**

```java
package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.watch.play.WatchLog;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: 视频播放验证
 * - 创建视频 → 通过 watchId 获取播放信息 → 验证播放页面可访问
 * - 验证 watchCount 和 WatchLog 记录
 */
class VideoWatchE2ETest extends BaseE2ETest {

    private JSONObject createTestVideo() {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", "e2e-watch-test.mp4");
        ResponseEntity<String> response = authPost("/video/create", body.toJSONString());
        JSONObject data = JSONObject.parseObject(response.getBody()).getJSONObject("data");
        createdVideoIds.add(data.getString("videoId"));
        createdFileIds.add(data.getString("fileId"));
        return data;
    }

    @Test
    void testGetWatchInfo() {
        JSONObject videoData = createTestVideo();
        String watchId = videoData.getString("watchId");
        String videoId = videoData.getString("videoId");

        // 通过 watchId 获取播放信息
        ResponseEntity<String> watchResponse = restTemplate.getForEntity(
                "/watchController/getWatchInfo?watchId=" + watchId, String.class);
        assertEquals(200, watchResponse.getStatusCode().value(), "获取播放信息应返回 200");

        JSONObject watchResult = JSONObject.parseObject(watchResponse.getBody());
        assertEquals(0, watchResult.getInteger("code"), "业务状态码应为 0");

        JSONObject watchData = watchResult.getJSONObject("data");
        assertNotNull(watchData, "播放信息不应为 null");
        assertEquals(videoId, watchData.getString("videoId"), "videoId 应匹配");
    }

    @Test
    void testWatchPageAccessible() {
        JSONObject videoData = createTestVideo();
        String watchId = videoData.getString("watchId");

        // 访问播放页面（Thymeleaf 模板）
        ResponseEntity<String> pageResponse = restTemplate.getForEntity(
                "/watch?v=" + watchId, String.class);
        assertEquals(200, pageResponse.getStatusCode().value(), "播放页面应返回 200");

        String html = pageResponse.getBody();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>") || html.contains("<html"),
                "应返回 HTML 页面");
    }

    @Test
    void testGetVideoDetail() {
        JSONObject videoData = createTestVideo();
        String videoId = videoData.getString("videoId");

        // 获取视频详情
        ResponseEntity<String> detailResponse = restTemplate.getForEntity(
                "/video/getVideoDetail?videoId=" + videoId, String.class);
        assertEquals(200, detailResponse.getStatusCode().value());

        JSONObject result = JSONObject.parseObject(detailResponse.getBody());
        JSONObject data = result.getJSONObject("data");
        assertEquals(videoId, data.getString("id"));
        assertEquals("USER_UPLOAD", data.getString("type"));
        assertNotNull(data.getString("watchId"));
        assertNotNull(data.getString("watchUrl"));
    }
}
```

**Step 2: 运行测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest=com.github.makewheels.video2022.e2e.VideoWatchE2ETest \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

Expected: All 3 tests PASS

**Step 3: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/VideoWatchE2ETest.java
git commit -m "test: 视频播放验证E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 7: 播放列表管理 E2E 测试

**Files:**
- Create: `video/src/test/java/com/github/makewheels/video2022/e2e/PlaylistE2ETest.java`

**Step 1: 编写测试**

```java
package com.github.makewheels.video2022.e2e;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.playlist.item.PlayItem;
import com.github.makewheels.video2022.playlist.list.bean.Playlist;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: 播放列表管理
 * - 创建播放列表 → 添加视频 → 查询列表 → 验证包含视频
 * - 验证 MongoDB 中 Playlist 和 PlayItem 记录
 */
class PlaylistE2ETest extends BaseE2ETest {

    private String createTestVideo(String filename) {
        JSONObject body = new JSONObject();
        body.put("videoType", "USER_UPLOAD");
        body.put("rawFilename", filename);
        ResponseEntity<String> response = authPost("/video/create", body.toJSONString());
        JSONObject data = JSONObject.parseObject(response.getBody()).getJSONObject("data");
        String videoId = data.getString("videoId");
        String fileId = data.getString("fileId");
        createdVideoIds.add(videoId);
        createdFileIds.add(fileId);
        return videoId;
    }

    @Test
    void testCreatePlaylistAndAddVideo() {
        // 创建一个视频
        String videoId = createTestVideo("e2e-playlist-test.mp4");

        // ── 创建播放列表 ──
        JSONObject createBody = new JSONObject();
        createBody.put("title", "E2E测试播放列表");
        createBody.put("description", "端到端测试创建的播放列表");

        ResponseEntity<String> createResponse = authPost(
                "/playlist/createPlaylist", createBody.toJSONString());
        assertEquals(200, createResponse.getStatusCode().value(), "创建播放列表应返回 200");

        JSONObject createResult = JSONObject.parseObject(createResponse.getBody());
        assertEquals(0, createResult.getInteger("code"));

        JSONObject playlistData = createResult.getJSONObject("data");
        String playlistId = playlistData.getString("id");
        assertNotNull(playlistId, "应返回 playlistId");
        createdPlaylistIds.add(playlistId);

        // 验证 MongoDB
        Playlist playlist = mongoTemplate.findById(playlistId, Playlist.class);
        assertNotNull(playlist, "MongoDB 中应有 Playlist 记录");
        assertEquals("E2E测试播放列表", playlist.getTitle());

        // ── 添加视频到播放列表 ──
        JSONObject addBody = new JSONObject();
        addBody.put("playlistId", playlistId);
        addBody.put("videoIdList", List.of(videoId));
        addBody.put("addMode", "ADD_TO_TOP");

        ResponseEntity<String> addResponse = authPost(
                "/playlist/addPlaylistItem", addBody.toJSONString());
        assertEquals(200, addResponse.getStatusCode().value(), "添加视频到列表应返回 200");

        // 验证 MongoDB 中 PlayItem 记录
        List<PlayItem> playItems = mongoTemplate.find(
                Query.query(Criteria.where("playlistId").is(playlistId)),
                PlayItem.class);
        assertFalse(playItems.isEmpty(), "应有 PlayItem 记录");
        assertEquals(videoId, playItems.get(0).getVideoId(), "PlayItem 的 videoId 应匹配");

        // ── 查询我的播放列表 ──
        ResponseEntity<String> listResponse = authGet(
                "/playlist/getMyPlaylistByPage?skip=0&limit=100");
        assertEquals(200, listResponse.getStatusCode().value());

        JSONObject listResult = JSONObject.parseObject(listResponse.getBody());
        JSONArray playlists = listResult.getJSONArray("data");

        boolean found = false;
        for (int i = 0; i < playlists.size(); i++) {
            JSONObject pl = playlists.getJSONObject(i);
            if (playlistId.equals(pl.getString("id"))) {
                found = true;
                assertEquals("E2E测试播放列表", pl.getString("title"));
                break;
            }
        }
        assertTrue(found, "我的播放列表中应包含刚创建的列表");
    }

    @Test
    void testAddMultipleVideosToPlaylist() {
        String videoId1 = createTestVideo("e2e-multi-1.mp4");
        String videoId2 = createTestVideo("e2e-multi-2.mp4");

        // 创建播放列表
        JSONObject createBody = new JSONObject();
        createBody.put("title", "E2E多视频列表");
        ResponseEntity<String> createResp = authPost("/playlist/createPlaylist", createBody.toJSONString());
        String playlistId = JSONObject.parseObject(createResp.getBody())
                .getJSONObject("data").getString("id");
        createdPlaylistIds.add(playlistId);

        // 添加两个视频
        JSONObject addBody = new JSONObject();
        addBody.put("playlistId", playlistId);
        addBody.put("videoIdList", List.of(videoId1, videoId2));
        addBody.put("addMode", "ADD_TO_TOP");

        ResponseEntity<String> addResp = authPost("/playlist/addPlaylistItem", addBody.toJSONString());
        assertEquals(200, addResp.getStatusCode().value());

        // 验证 MongoDB 中有两个 PlayItem
        List<PlayItem> playItems = mongoTemplate.find(
                Query.query(Criteria.where("playlistId").is(playlistId)),
                PlayItem.class);
        assertEquals(2, playItems.size(), "应有 2 个 PlayItem 记录");
    }
}
```

**Step 2: 运行测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest=com.github.makewheels.video2022.e2e.PlaylistE2ETest \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

Expected: All 2 tests PASS

**Step 3: 提交**

```bash
git add video/src/test/java/com/github/makewheels/video2022/e2e/PlaylistE2ETest.java
git commit -m "test: 播放列表管理E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 8: Playwright 浏览器 E2E 测试

**Files:**
- Create: `video/src/test/playwright/tests/e2e-flow.spec.js`

**注意:** Playwright 测试需要服务器运行在 localhost:5022。在运行前需要先启动应用。

**Step 1: 编写 Playwright E2E 测试**

```javascript
// @ts-check
const { test, expect } = require('@playwright/test');

const BASE_URL = 'http://localhost:5022';
const TEST_PHONE = '19900009999';

/**
 * E2E 浏览器测试
 * 测试真实的用户交互流程，需要服务器运行在 localhost:5022
 */

test.describe('E2E: 登录流程', () => {
    test('输入手机号→发送验证码→登录→跳转', async ({ page }) => {
        await page.goto(BASE_URL + '/login.html');

        // 输入手机号
        await page.fill('#input_phone', TEST_PHONE);
        const phoneValue = await page.inputValue('#input_phone');
        expect(phoneValue).toBe(TEST_PHONE);

        // 点击发送验证码
        await page.click('#button_send_code');
        // 等待 toast 提示
        await expect(page.locator('.toast')).toBeVisible({ timeout: 3000 });

        // 输入验证码（dev 模式 111 万能通过）
        await page.fill('#input_code', '111');

        // 点击登录
        await page.click('#button_login');

        // 等待跳转或 toast 提示
        await page.waitForTimeout(2000);

        // 验证：应该跳转到其他页面或显示成功 toast
        const url = page.url();
        const hasLoginSuccess = url !== BASE_URL + '/login.html'
            || await page.locator('.toast-success').count() > 0;
        expect(hasLoginSuccess).toBeTruthy();
    });

    test('手机号格式验证', async ({ page }) => {
        await page.goto(BASE_URL + '/login.html');

        // 输入无效手机号
        await page.fill('#input_phone', '123');
        await page.click('#button_send_code');

        // 应显示错误 toast
        await expect(page.locator('.toast-error')).toBeVisible({ timeout: 3000 });
    });
});

test.describe('E2E: 首页', () => {
    test('首页可访问且有导航链接', async ({ page }) => {
        await page.goto(BASE_URL + '/index.html');
        await expect(page).toHaveTitle(/video|视频/i);

        // 验证导航链接存在
        const links = page.locator('a.nav-link');
        const count = await links.count();
        expect(count).toBeGreaterThanOrEqual(1);
    });
});

test.describe('E2E: 上传页面', () => {
    test('未登录访问上传页重定向到登录页', async ({ page }) => {
        // 清除所有 cookie 和 localStorage
        await page.context().clearCookies();

        await page.goto(BASE_URL + '/upload.html');
        await page.waitForTimeout(2000);

        // 因为需要认证，应重定向到登录页
        const url = page.url();
        expect(url).toContain('login.html');
    });
});
```

**Step 2: 运行 Playwright 测试**

需要先确保服务器在 localhost:5022 运行：
```bash
# 构建 JAR
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn clean package -pl video -Pspringboot -Dmaven.test.skip=true -q

# 启动服务器（后台）
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
java -jar video/target/video-0.0.1-SNAPSHOT.jar &

# 等待启动
sleep 15

# 运行 Playwright 测试
cd video/src/test/playwright && npx playwright test tests/e2e-flow.spec.js --reporter=list
```

Expected: All tests PASS

**Step 3: 提交**

```bash
git add video/src/test/playwright/tests/e2e-flow.spec.js
git commit -m "test: Playwright浏览器E2E测试

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 9: 运行全部 E2E 测试并修复问题

**Step 1: 运行所有 Java E2E 测试**

```bash
export $(grep -v '^#' .env | grep -v '^$' | xargs) && \
mvn test -pl video -Pspringboot \
  -Dtest="com.github.makewheels.video2022.e2e.*" \
  -Dlog4j.configuration=file:///tmp/log4j.properties
```

**Step 2: 修复所有失败的测试**

根据错误信息调整测试代码或发现的 bug。

**Step 3: 确认所有测试通过后提交**

```bash
git add -A
git commit -m "fix: 修复E2E测试问题

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 10: 调查手机号 `****` 问题

**Step 1: 用 Playwright 打开登录页，截图观察**

```javascript
// 在 Playwright 中打开登录页检查 input 样式
const { chromium } = require('playwright');
(async () => {
    const browser = await chromium.launch({ headless: false });
    const page = await browser.newPage();
    await page.goto('http://localhost:5022/login.html');
    await page.fill('#input_phone', '13800138000');
    await page.screenshot({ path: 'phone-test.png' });
    // 检查 computed style
    const style = await page.evaluate(() => {
        const input = document.querySelector('#input_phone');
        const computed = window.getComputedStyle(input);
        return {
            webkitTextSecurity: computed.webkitTextSecurity,
            type: input.type,
            value: input.value
        };
    });
    console.log('Input style:', style);
})();
```

**Step 2: 如果发现 CSS 或 HTML 问题，修复**

可能的修复：
- 如果 `type` 被设置为 `password`，改回 `tel`
- 如果有 `-webkit-text-security` CSS，移除
- 如果是浏览器自动填充导致，添加 `autocomplete="off"`

**Step 3: 提交修复**

```bash
git add -A
git commit -m "fix: 修复手机号输入框显示问题

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 11: 推送并创建 PR

**Step 1: 推送分支**

```bash
git push origin feat/e2e-tests
```

**Step 2: 创建 PR**

```bash
gh pr create --title "test: E2E端到端测试 - 登录、上传、修改、播放、播放列表" \
  --body "## 变更内容
  
### 新增 E2E 测试体系
- BaseE2ETest 基类：真实 MongoDB + 真实 OSS，自动清理
- LoginE2ETest：登录注册流程（4 个测试）
- VideoUploadE2ETest：视频创建上传全流程（2 个测试）
- VideoModifyE2ETest：视频信息修改（2 个测试）
- VideoWatchE2ETest：视频播放验证（3 个测试）
- PlaylistE2ETest：播放列表管理（2 个测试）
- Playwright 浏览器 E2E 测试（3 个测试）

### 运行方式
Java E2E:
\`\`\`bash
export \$(grep -v '^#' .env | grep -v '^\$' | xargs) && mvn test -pl video -Pspringboot -Dtest='com.github.makewheels.video2022.e2e.*'
\`\`\`

Playwright E2E（需要先启动服务器）:
\`\`\`bash
cd video/src/test/playwright && npx playwright test tests/e2e-flow.spec.js
\`\`\`
"
```
