package com.github.makewheels.video2022.openapi;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthToken;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OAuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private DeveloperService developerService;

    @Autowired
    private OAuthAppService oAuthAppService;

    @Autowired
    private OAuthTokenService oAuthTokenService;

    private Developer developer;
    private OAuthApp app;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        // Shared setup: register a developer and create an OAuth app
        developer = developerService.register("oauth@example.com", "password123", "OAuth测试", null);
        app = oAuthAppService.createApp(
                developer.getId(), "测试应用", "OAuth测试应用",
                List.of("https://example.com/callback"),
                List.of("video:read", "video:write")
        );
        // createApp temporarily puts the plaintext secret in clientSecretHash
        clientSecret = app.getClientSecretHash();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ---- validateClientCredentials ----

    @Test
    void validateClientCredentials_validCredentials_returnsApp() {
        OAuthApp validated = oAuthAppService.validateClientCredentials(app.getClientId(), clientSecret);
        assertNotNull(validated);
        assertEquals(app.getId(), validated.getId());
        assertEquals("active", validated.getStatus());
    }

    @Test
    void validateClientCredentials_wrongSecret_returnsNull() {
        OAuthApp result = oAuthAppService.validateClientCredentials(app.getClientId(), "wrong-secret");
        assertNull(result);
    }

    @Test
    void validateClientCredentials_unknownClientId_returnsNull() {
        OAuthApp result = oAuthAppService.validateClientCredentials("nonexistent-client-id", clientSecret);
        assertNull(result);
    }

    @Test
    void validateClientCredentials_inactiveApp_returnsNull() {
        // Directly update the app status in MongoDB to simulate suspension
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(app.getId())),
                Update.update("status", "suspended"),
                OAuthApp.class
        );

        OAuthApp result = oAuthAppService.validateClientCredentials(app.getClientId(), clientSecret);
        assertNull(result);
    }

    // ---- issueToken ----

    @Test
    void issueToken_createsTokenWithCorrectFields() {
        List<String> scopes = List.of("video:read");
        OAuthToken token = oAuthTokenService.issueToken(app.getId(), "user-123", scopes);

        assertNotNull(token.getId());
        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());
        assertEquals(app.getId(), token.getAppId());
        assertEquals("user-123", token.getUserId());
        assertEquals(scopes, token.getScopes());
        assertNotNull(token.getExpiresAt());
        assertTrue(token.getExpiresAt().after(new Date()));
    }

    @Test
    void issueToken_expiresInAboutTwoHours() {
        OAuthToken token = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));

        long twoHoursMs = 2 * 3600 * 1000L;
        long diff = token.getExpiresAt().getTime() - System.currentTimeMillis();
        // Allow 5-second tolerance for test execution time
        assertTrue(diff > twoHoursMs - 5000 && diff <= twoHoursMs,
                "Token expiration should be approximately 2 hours from now, but diff=" + diff + "ms");
    }

    @Test
    void issueToken_generatesUniqueTokens() {
        OAuthToken token1 = oAuthTokenService.issueToken(app.getId(), "user-1", List.of("video:read"));
        OAuthToken token2 = oAuthTokenService.issueToken(app.getId(), "user-2", List.of("video:read"));

        assertNotEquals(token1.getAccessToken(), token2.getAccessToken());
        assertNotEquals(token1.getRefreshToken(), token2.getRefreshToken());
    }

    // ---- validateAccessToken ----

    @Test
    void validateAccessToken_validToken_returnsToken() {
        OAuthToken issued = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));
        OAuthToken validated = oAuthTokenService.validateAccessToken(issued.getAccessToken());

        assertNotNull(validated);
        assertEquals(issued.getId(), validated.getId());
        assertEquals(issued.getAccessToken(), validated.getAccessToken());
    }

    @Test
    void validateAccessToken_nonexistentToken_returnsNull() {
        OAuthToken result = oAuthTokenService.validateAccessToken("nonexistent-token");
        assertNull(result);
    }

    @Test
    void validateAccessToken_expiredToken_returnsNull() {
        OAuthToken token = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));

        // Force-expire the token by updating expiresAt to the past
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(token.getId())),
                Update.update("expiresAt", new Date(System.currentTimeMillis() - 1000)),
                OAuthToken.class
        );

        OAuthToken result = oAuthTokenService.validateAccessToken(token.getAccessToken());
        assertNull(result);
    }

    // ---- refreshToken ----

    @Test
    void refreshToken_validRefreshToken_issuesNewToken() {
        OAuthToken original = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));
        String originalRefreshToken = original.getRefreshToken();

        OAuthToken refreshed = oAuthTokenService.refreshToken(originalRefreshToken);

        assertNotNull(refreshed);
        assertNotEquals(original.getAccessToken(), refreshed.getAccessToken());
        assertNotEquals(original.getRefreshToken(), refreshed.getRefreshToken());
        assertEquals(original.getAppId(), refreshed.getAppId());
        assertEquals(original.getUserId(), refreshed.getUserId());
        assertEquals(original.getScopes(), refreshed.getScopes());
    }

    @Test
    void refreshToken_invalidatesOldToken() {
        OAuthToken original = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));

        oAuthTokenService.refreshToken(original.getRefreshToken());

        // Old access token should no longer be valid
        OAuthToken result = oAuthTokenService.validateAccessToken(original.getAccessToken());
        assertNull(result);
    }

    @Test
    void refreshToken_invalidRefreshToken_throwsException() {
        assertThrows(RuntimeException.class, () ->
                oAuthTokenService.refreshToken("invalid-refresh-token")
        );
    }

    // ---- revokeToken ----

    @Test
    void revokeToken_validToken_becomesInvalid() {
        OAuthToken token = oAuthTokenService.issueToken(app.getId(), "user-123", List.of("video:read"));

        oAuthTokenService.revokeToken(token.getAccessToken());

        OAuthToken result = oAuthTokenService.validateAccessToken(token.getAccessToken());
        assertNull(result);
    }

    @Test
    void revokeToken_nonexistentToken_doesNotThrow() {
        // Revoking a non-existent token should be a no-op
        assertDoesNotThrow(() -> oAuthTokenService.revokeToken("nonexistent-token"));
    }
}
