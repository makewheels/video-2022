package com.github.makewheels.video2022.etc.app;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.system.response.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class AppServiceTest extends BaseIntegrationTest {

    @Autowired
    private AppService appService;

    @Autowired
    private AppVersionRepository appVersionRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private AppVersion saveVersion(String platform, int code, String name) {
        AppVersion v = new AppVersion();
        v.setPlatform(platform);
        v.setVersionCode(code);
        v.setVersionName(name);
        v.setDownloadUrl("https://example.com/" + platform + "-" + name + ".apk");
        v.setVersionInfo("Release " + name);
        return appVersionRepository.save(v);
    }

    // ──────────────── checkUpdate tests ────────────────

    @Test
    void checkUpdate_noVersionsExist_hasUpdateFalse() {
        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 1);

        assertEquals(0, result.getCode());
        CheckUpdateResponse data = result.getData();
        assertNotNull(data);
        assertFalse(data.getHasUpdate());
    }

    @Test
    void checkUpdate_clientOlder_hasUpdateTrue() {
        saveVersion("android", 10, "2.0.0");

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 5);

        CheckUpdateResponse data = result.getData();
        assertTrue(data.getHasUpdate());
        assertEquals(10, data.getVersionCode());
        assertEquals("2.0.0", data.getVersionName());
        assertEquals("Release 2.0.0", data.getVersionInfo());
        assertEquals("https://example.com/android-2.0.0.apk", data.getDownloadUrl());
    }

    @Test
    void checkUpdate_clientSameVersion_hasUpdateFalse() {
        saveVersion("android", 10, "2.0.0");

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 10);

        assertFalse(result.getData().getHasUpdate());
    }

    @Test
    void checkUpdate_clientNewer_hasUpdateFalse() {
        saveVersion("android", 10, "2.0.0");

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 15);

        assertFalse(result.getData().getHasUpdate());
    }

    @Test
    void checkUpdate_forceUpdateFlag_isForceUpdateTrue() {
        AppVersion v = saveVersion("android", 10, "2.0.0");
        v.setIsForceUpdate(true);
        appVersionRepository.save(v);

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 5);

        assertTrue(result.getData().getHasUpdate());
        assertTrue(result.getData().getIsForceUpdate());
    }

    @Test
    void checkUpdate_belowMinSupportedVersionCode_isForceUpdateTrue() {
        AppVersion v = saveVersion("android", 10, "2.0.0");
        v.setMinSupportedVersionCode(5);
        appVersionRepository.save(v);

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 3);

        assertTrue(result.getData().getHasUpdate());
        assertTrue(result.getData().getIsForceUpdate());
    }

    @Test
    void checkUpdate_aboveMinSupportedVersionCode_isForceUpdateFalse() {
        AppVersion v = saveVersion("android", 10, "2.0.0");
        v.setMinSupportedVersionCode(5);
        appVersionRepository.save(v);

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 7);

        assertTrue(result.getData().getHasUpdate());
        assertFalse(result.getData().getIsForceUpdate());
    }

    @Test
    void checkUpdate_multipleVersions_returnsLatest() {
        saveVersion("android", 5, "1.0.0");
        saveVersion("android", 15, "3.0.0");
        saveVersion("android", 10, "2.0.0");

        Result<CheckUpdateResponse> result = appService.checkUpdate("android", 1);

        CheckUpdateResponse data = result.getData();
        assertTrue(data.getHasUpdate());
        assertEquals(15, data.getVersionCode());
        assertEquals("3.0.0", data.getVersionName());
    }

    // ──────────────── publishVersion tests ────────────────

    @Test
    void publishVersion_createsNewVersionCorrectly() {
        PublishVersionRequest request = new PublishVersionRequest();
        request.setPlatform("ios");
        request.setVersionCode(1);
        request.setVersionName("1.0.0");
        request.setVersionInfo("First iOS release");
        request.setDownloadUrl("https://example.com/ios-1.0.0.ipa");
        request.setIsForceUpdate(false);
        request.setMinSupportedVersionCode(1);

        Result<AppVersion> result = appService.publishVersion(request);

        assertEquals(0, result.getCode());
        AppVersion saved = result.getData();
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("ios", saved.getPlatform());
        assertEquals(1, saved.getVersionCode());
        assertEquals("1.0.0", saved.getVersionName());
        assertEquals("First iOS release", saved.getVersionInfo());
        assertEquals("https://example.com/ios-1.0.0.ipa", saved.getDownloadUrl());
        assertFalse(saved.getIsForceUpdate());
        assertEquals(1, saved.getMinSupportedVersionCode());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
    }

    @Test
    void publishVersion_allFieldsPersistedToMongoDB() {
        PublishVersionRequest request = new PublishVersionRequest();
        request.setPlatform("android");
        request.setVersionCode(20);
        request.setVersionName("4.0.0");
        request.setVersionInfo("Major update");
        request.setDownloadUrl("https://example.com/android-4.0.0.apk");
        request.setIsForceUpdate(true);
        request.setMinSupportedVersionCode(10);

        Result<AppVersion> result = appService.publishVersion(request);
        String id = result.getData().getId();

        AppVersion fromDb = mongoTemplate.findById(id, AppVersion.class);
        assertNotNull(fromDb);
        assertEquals("android", fromDb.getPlatform());
        assertEquals(20, fromDb.getVersionCode());
        assertEquals("4.0.0", fromDb.getVersionName());
        assertEquals("Major update", fromDb.getVersionInfo());
        assertEquals("https://example.com/android-4.0.0.apk", fromDb.getDownloadUrl());
        assertTrue(fromDb.getIsForceUpdate());
        assertEquals(10, fromDb.getMinSupportedVersionCode());
        assertNotNull(fromDb.getCreateTime());
        assertNotNull(fromDb.getUpdateTime());
    }
}
