package com.github.makewheels.video2022.developer;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.developer.dto.UpdateDeveloperAppRequest;
import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import com.github.makewheels.video2022.developer.service.DeveloperAppService;
import com.github.makewheels.video2022.developer.service.DeveloperTokenService;
import com.github.makewheels.video2022.developer.service.DeveloperWebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DeveloperAppServiceTest extends BaseIntegrationTest {

    @Autowired
    private DeveloperAppService developerAppService;

    @Autowired
    private DeveloperTokenService developerTokenService;

    @Autowired
    private DeveloperWebhookService developerWebhookService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ---- Create App ----

    @Test
    void createApp_success() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        assertNotNull(app.getId());
        assertNotNull(app.getAppId());
        assertNotNull(app.getAppSecret());
        assertEquals(32, app.getAppSecret().length());
        assertNotNull(app.getWebhookSecret());
        assertEquals("测试应用", app.getAppName());
        assertEquals("user1", app.getUserId());
        assertEquals("active", app.getStatus());
    }

    // ---- List Apps ----

    @Test
    void listApps_returnsOnlyOwnApps() {
        developerAppService.createApp("user1", "应用1");
        developerAppService.createApp("user2", "应用2");
        developerAppService.createApp("user1", "应用3");

        List<DeveloperApp> user1Apps = developerAppService.listByUserId("user1");
        assertEquals(2, user1Apps.size());

        List<DeveloperApp> user2Apps = developerAppService.listByUserId("user2");
        assertEquals(1, user2Apps.size());
        assertEquals("应用2", user2Apps.get(0).getAppName());
    }

    // ---- Update App ----

    @Test
    void updateApp_webhookUrl_success() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");

        UpdateDeveloperAppRequest request = new UpdateDeveloperAppRequest();
        request.setWebhookUrl("https://example.com/webhook");
        request.setWebhookEvents(List.of("video.created", "video.transcoded"));

        DeveloperApp updated = developerAppService.updateApp("user1", app.getAppId(), request);
        assertEquals("https://example.com/webhook", updated.getWebhookUrl());
        assertEquals(2, updated.getWebhookEvents().size());
        assertTrue(updated.getWebhookEvents().contains("video.created"));
    }

    @Test
    void updateApp_wrongUser_throwsException() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        UpdateDeveloperAppRequest request = new UpdateDeveloperAppRequest();
        request.setWebhookUrl("https://example.com/webhook");

        assertThrows(RuntimeException.class, () ->
                developerAppService.updateApp("user2", app.getAppId(), request)
        );
    }

    // ---- Delete App ----

    @Test
    void deleteApp_success() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        developerAppService.deleteApp("user1", app.getAppId());

        List<DeveloperApp> apps = developerAppService.listByUserId("user1");
        assertTrue(apps.isEmpty());
    }

    @Test
    void deleteApp_wrongUser_throwsException() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        assertThrows(RuntimeException.class, () ->
                developerAppService.deleteApp("user2", app.getAppId())
        );
    }

    @Test
    void deleteApp_nonexistent_throwsException() {
        assertThrows(RuntimeException.class, () ->
                developerAppService.deleteApp("user1", "nonexistent-app-id")
        );
    }

    // ---- Regenerate Secret ----

    @Test
    void regenerateSecret_changesSecret() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        String originalSecret = app.getAppSecret();

        DeveloperApp updated = developerAppService.regenerateSecret("user1", app.getAppId());
        assertNotEquals(originalSecret, updated.getAppSecret());
        assertEquals(32, updated.getAppSecret().length());
    }

    // ---- JWT Token Issue & Verify ----

    @Test
    void createToken_validCredentials_returnsToken() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        String token = developerTokenService.createToken(app.getAppId(), app.getAppSecret());
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void createToken_invalidSecret_throwsException() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        assertThrows(RuntimeException.class, () ->
                developerTokenService.createToken(app.getAppId(), "wrong-secret")
        );
    }

    @Test
    void createToken_invalidAppId_throwsException() {
        assertThrows(RuntimeException.class, () ->
                developerTokenService.createToken("nonexistent-app-id", "any-secret")
        );
    }

    @Test
    void verifyToken_validToken_returnsClaims() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        String token = developerTokenService.createToken(app.getAppId(), app.getAppSecret());

        Map<String, Object> claims = developerTokenService.verifyToken(token);
        assertNotNull(claims);
        assertEquals(app.getAppId(), claims.get("appId"));
        assertEquals("user1", claims.get("userId"));
        assertEquals(true, claims.get("valid"));
    }

    @Test
    void verifyToken_invalidToken_returnsNull() {
        Map<String, Object> claims = developerTokenService.verifyToken("invalid.jwt.token");
        assertNull(claims);
    }

    @Test
    void verifyToken_afterRegenerateSecret_oldTokenStillValid() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        String token = developerTokenService.createToken(app.getAppId(), app.getAppSecret());

        // Regenerate the secret — the JWT is signed with a global key, not the app secret
        developerAppService.regenerateSecret("user1", app.getAppId());

        // Token should still be valid (signed with developer.jwt-secret, not appSecret)
        Map<String, Object> claims = developerTokenService.verifyToken(token);
        assertNotNull(claims);
        assertEquals(app.getAppId(), claims.get("appId"));
    }

    // ---- Webhook Signature ----

    @Test
    void sendWebhookEvent_noUrl_doesNotThrow() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        // No webhook URL configured, should not throw
        assertDoesNotThrow(() ->
                developerWebhookService.sendWebhookEvent(app.getAppId(), "video.created",
                        Map.of("videoId", "v1"))
        );
    }

    @Test
    void sendWebhookEvent_notSubscribed_doesNotThrow() {
        DeveloperApp app = developerAppService.createApp("user1", "测试应用");
        UpdateDeveloperAppRequest req = new UpdateDeveloperAppRequest();
        req.setWebhookUrl("https://example.com/webhook");
        req.setWebhookEvents(List.of("video.created"));
        developerAppService.updateApp("user1", app.getAppId(), req);

        // Event not subscribed to
        assertDoesNotThrow(() ->
                developerWebhookService.sendWebhookEvent(app.getAppId(), "video.deleted",
                        Map.of("videoId", "v1"))
        );
    }
}
