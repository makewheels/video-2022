package com.github.makewheels.video2022.share;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.bean.User;
import com.github.makewheels.video2022.video.bean.entity.Video;
import com.github.makewheels.video2022.video.constants.VideoStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShareServiceTest extends BaseIntegrationTest {

    @Autowired
    private ShareLinkService shareLinkService;

    private User testUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        testUser = new User();
        testUser.setId(new ObjectId().toHexString());
        testUser.setPhone("13800000055");
        mongoTemplate.save(testUser);
        UserHolder.set(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.remove();
    }

    private Video createTestVideo(String videoId) {
        Video video = new Video();
        video.setId(videoId);
        video.setTitle("Test Video");
        video.setStatus(VideoStatus.READY);
        video.setUploaderId(testUser.getId());
        return mongoTemplate.save(video);
    }

    @Test
    void createShareLink_success() {
        String videoId = "v_share_001";
        createTestVideo(videoId);

        Result<ShareLink> result = shareLinkService.createShareLink(videoId);

        assertEquals(0, result.getCode());
        ShareLink link = result.getData();
        assertNotNull(link);
        assertEquals(videoId, link.getVideoId());
        assertEquals(testUser.getId(), link.getCreatedBy());
        assertNotNull(link.getShortCode());
        assertEquals(8, link.getShortCode().length());
        assertEquals(0, link.getClickCount());
        assertNotNull(link.getCreateTime());
    }

    @Test
    void createShareLink_videoNotExist_returnsError() {
        Result<ShareLink> result = shareLinkService.createShareLink("nonexistent");

        assertEquals(22, result.getCode());
    }

    @Test
    void createShareLink_sameVideoSameUser_reusesLink() {
        String videoId = "v_share_002";
        createTestVideo(videoId);

        Result<ShareLink> first = shareLinkService.createShareLink(videoId);
        Result<ShareLink> second = shareLinkService.createShareLink(videoId);

        assertEquals(first.getData().getShortCode(), second.getData().getShortCode());
        assertEquals(first.getData().getId(), second.getData().getId());
    }

    @Test
    void resolveShortCode_incrementsClickCount() {
        String videoId = "v_share_003";
        createTestVideo(videoId);
        Result<ShareLink> createResult = shareLinkService.createShareLink(videoId);
        String shortCode = createResult.getData().getShortCode();

        ShareLink resolved = shareLinkService.resolveShortCode(shortCode, "https://twitter.com");

        assertNotNull(resolved);
        assertEquals(videoId, resolved.getVideoId());

        // Check click count was incremented
        Result<ShareLink> stats = shareLinkService.getStats(shortCode);
        assertEquals(1, stats.getData().getClickCount());
        assertEquals("https://twitter.com", stats.getData().getLastReferrer());
    }

    @Test
    void resolveShortCode_notFound_returnsNull() {
        ShareLink result = shareLinkService.resolveShortCode("nonexist", null);
        assertNull(result);
    }

    @Test
    void getStats_success() {
        String videoId = "v_share_004";
        createTestVideo(videoId);
        Result<ShareLink> createResult = shareLinkService.createShareLink(videoId);
        String shortCode = createResult.getData().getShortCode();

        // Click 3 times
        shareLinkService.resolveShortCode(shortCode, "https://weibo.com");
        shareLinkService.resolveShortCode(shortCode, "https://wechat.com");
        shareLinkService.resolveShortCode(shortCode, null);

        Result<ShareLink> stats = shareLinkService.getStats(shortCode);

        assertEquals(0, stats.getCode());
        assertEquals(3, stats.getData().getClickCount());
    }

    @Test
    void getStats_notFound_returnsError() {
        Result<ShareLink> result = shareLinkService.getStats("nosuchcode");
        assertNotEquals(0, result.getCode());
    }
}
