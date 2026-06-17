package com.github.makewheels.video2022.cover;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.cover.local.LocalCoverService;
import com.github.makewheels.video2022.cover.local.dto.CreateLocalCoverRequest;
import com.github.makewheels.video2022.file.bean.File;
import com.github.makewheels.video2022.file.constants.FileStatus;
import com.github.makewheels.video2022.file.constants.FileType;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import com.github.makewheels.video2022.video.constants.VideoType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LocalCoverServiceTest extends BaseIntegrationTest {

    @Autowired
    private LocalCoverService localCoverService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setPhone("13800000003");
        testUser.setRegisterChannel("TEST");
        testUser.setToken("test-token-local-cover");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);

        JSONObject creds = new JSONObject();
        creds.put("bucket", "video-2022-test");
        creds.put("endpoint", "oss-cn-beijing.aliyuncs.com");
        creds.put("accessKeyId", "STS.mockAk");
        creds.put("secretKey", "mockSk");
        creds.put("sessionToken", "mockToken");
        when(ossVideoService.generateUploadCredentials(anyString())).thenReturn(creds);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
        cleanDatabase();
    }

    private Video createVideo() {
        Video video = new Video();
        video.setId("v_cover");
        video.setUploaderId(testUser.getId());
        video.setVideoType(VideoType.USER_UPLOAD);
        video.setStatus(VideoStatus.TRANSCODING);
        mongoTemplate.save(video);
        return video;
    }

    @Test
    void createAndFinishCover_attachesReadyCoverToVideo_withoutMps() {
        Video video = createVideo();

        CreateLocalCoverRequest request = new CreateLocalCoverRequest();
        request.setVideoId(video.getId());
        request.setExtension("jpg");
        JSONObject response = localCoverService.createCover(request);

        String coverId = response.getString("coverId");
        assertNotNull(coverId);
        assertNotNull(response.getString("coverKey"));
        assertNotNull(response.getJSONObject("uploadCredentials"));

        Cover cover = mongoTemplate.findById(coverId, Cover.class);
        assertNotNull(cover);
        assertEquals(CoverProvider.LOCAL, cover.getProvider());
        assertEquals(CoverStatus.CREATED, cover.getStatus());
        assertEquals(response.getString("coverKey"), cover.getKey());

        File coverFile = mongoTemplate.findById(cover.getFileId(), File.class);
        assertNotNull(coverFile);
        assertEquals(FileType.COVER, coverFile.getFileType());

        localCoverService.finishCover(coverId);

        Cover finished = mongoTemplate.findById(coverId, Cover.class);
        assertEquals(CoverStatus.READY, finished.getStatus());
        assertNotNull(finished.getFinishTime());
        assertEquals(FileStatus.READY,
                mongoTemplate.findById(cover.getFileId(), File.class).getFileStatus());
        assertEquals(coverId, mongoTemplate.findById(video.getId(), Video.class).getCoverId());

        verifyNoInteractions(aliyunMpsService);
    }
}
