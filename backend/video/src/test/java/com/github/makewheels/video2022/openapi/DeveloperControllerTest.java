package com.github.makewheels.video2022.openapi;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeveloperControllerTest extends BaseIntegrationTest {

    @Autowired
    private DeveloperService developerService;

    @Autowired
    private OAuthAppService oAuthAppService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ---- Register ----

    @Test
    void register_createsNewDeveloper() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", "测试公司");
        assertNotNull(dev.getId());
        assertEquals("test@example.com", dev.getEmail());
        assertEquals("测试用户", dev.getName());
        assertEquals("测试公司", dev.getCompany());
        assertEquals("active", dev.getStatus());
    }

    @Test
    void register_duplicateEmail_throwsException() {
        developerService.register("test@example.com", "password123", "用户1", null);
        assertThrows(RuntimeException.class, () ->
                developerService.register("test@example.com", "password456", "用户2", null)
        );
    }

    // ---- Login ----

    @Test
    void login_validCredentials_returnsToken() {
        developerService.register("test@example.com", "password123", "测试用户", null);
        String token = developerService.login("test@example.com", "password123");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void login_wrongPassword_throwsException() {
        developerService.register("test@example.com", "password123", "测试用户", null);
        assertThrows(RuntimeException.class, () ->
                developerService.login("test@example.com", "wrongpassword")
        );
    }

    @Test
    void login_nonexistentEmail_throwsException() {
        assertThrows(RuntimeException.class, () ->
                developerService.login("nobody@example.com", "password123")
        );
    }

    // ---- JWT Validation ----

    @Test
    void validateJwt_validToken_returnsDeveloperId() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", null);
        String token = developerService.login("test@example.com", "password123");
        String developerId = developerService.validateJwt(token);
        assertEquals(dev.getId(), developerId);
    }

    @Test
    void validateJwt_invalidToken_returnsNull() {
        String result = developerService.validateJwt("invalid.token.here");
        assertNull(result);
    }

    // ---- Developer Lookup ----

    @Test
    void getById_existingDeveloper_returnsDeveloper() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", null);
        Developer found = developerService.getById(dev.getId());
        assertNotNull(found);
        assertEquals(dev.getEmail(), found.getEmail());
    }

    @Test
    void findByEmail_existingEmail_returnsDeveloper() {
        developerService.register("test@example.com", "password123", "测试用户", null);
        Developer found = developerService.findByEmail("test@example.com");
        assertNotNull(found);
        assertEquals("测试用户", found.getName());
    }

    // ---- OAuth App Management ----

    @Test
    void createApp_validRequest_createsApp() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", null);
        OAuthApp app = oAuthAppService.createApp(
                dev.getId(), "测试应用", "用于测试",
                List.of("https://example.com/callback"),
                List.of("video:read")
        );
        assertNotNull(app.getId());
        assertNotNull(app.getClientId());
        assertEquals("测试应用", app.getName());
        assertEquals(dev.getId(), app.getDeveloperId());
    }

    @Test
    void listApps_returnsOnlyOwnApps() {
        Developer dev1 = developerService.register("dev1@example.com", "password123", "开发者1", null);
        Developer dev2 = developerService.register("dev2@example.com", "password123", "开发者2", null);

        oAuthAppService.createApp(dev1.getId(), "应用1", "desc",
                List.of("https://a.com/cb"), List.of("video:read"));
        oAuthAppService.createApp(dev2.getId(), "应用2", "desc",
                List.of("https://b.com/cb"), List.of("video:read"));

        List<OAuthApp> dev1Apps = oAuthAppService.getAppsByDeveloperId(dev1.getId());
        assertEquals(1, dev1Apps.size());
        assertEquals("应用1", dev1Apps.get(0).getName());
    }

    @Test
    void deleteApp_ownApp_succeeds() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", null);
        OAuthApp app = oAuthAppService.createApp(
                dev.getId(), "测试应用", "desc",
                List.of("https://example.com/callback"),
                List.of("video:read")
        );

        oAuthAppService.deleteApp(dev.getId(), app.getId());

        List<OAuthApp> apps = oAuthAppService.getAppsByDeveloperId(dev.getId());
        assertTrue(apps.isEmpty());
    }

    @Test
    void deleteApp_otherDeveloperApp_throwsException() {
        Developer dev1 = developerService.register("dev1@example.com", "password123", "开发者1", null);
        Developer dev2 = developerService.register("dev2@example.com", "password123", "开发者2", null);

        OAuthApp app = oAuthAppService.createApp(
                dev1.getId(), "应用1", "desc",
                List.of("https://a.com/cb"), List.of("video:read")
        );

        assertThrows(RuntimeException.class, () ->
                oAuthAppService.deleteApp(dev2.getId(), app.getId())
        );
    }

    @Test
    void deleteApp_nonexistentApp_throwsException() {
        Developer dev = developerService.register("test@example.com", "password123", "测试用户", null);
        assertThrows(RuntimeException.class, () ->
                oAuthAppService.deleteApp(dev.getId(), "nonexistent-id")
        );
    }
}
