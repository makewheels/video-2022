package com.github.makewheels.video2022.etc.app;

import com.github.makewheels.video2022.system.response.Result;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AppService {
    @Resource
    private AppVersionRepository appVersionRepository;

    /**
     * 检查更新
     */
    public Result<CheckUpdateResponse> checkUpdate(String platform, Integer versionCode) {
        AppVersion latest = appVersionRepository.findLatestByPlatform(platform);
        CheckUpdateResponse response = new CheckUpdateResponse();

        if (latest == null) {
            response.setHasUpdate(false);
            return Result.ok(response);
        }

        response.setVersionCode(latest.getVersionCode());
        response.setVersionName(latest.getVersionName());
        response.setVersionInfo(latest.getVersionInfo());
        response.setDownloadUrl(latest.getDownloadUrl());

        boolean hasUpdate = versionCode != null && versionCode < latest.getVersionCode();
        response.setHasUpdate(hasUpdate);

        // 判断是否强制更新
        boolean forceUpdate = false;
        if (hasUpdate) {
            if (Boolean.TRUE.equals(latest.getIsForceUpdate())) {
                forceUpdate = true;
            }
            if (latest.getMinSupportedVersionCode() != null
                    && versionCode < latest.getMinSupportedVersionCode()) {
                forceUpdate = true;
            }
        }
        response.setIsForceUpdate(forceUpdate);

        return Result.ok(response);
    }

    /**
     * 发布新版本（CI/CD调用）
     */
    public Result<AppVersion> publishVersion(PublishVersionRequest request) {
        AppVersion appVersion = new AppVersion();
        appVersion.setPlatform(request.getPlatform());
        appVersion.setVersionCode(request.getVersionCode());
        appVersion.setVersionName(request.getVersionName());
        appVersion.setVersionInfo(request.getVersionInfo());
        appVersion.setDownloadUrl(request.getDownloadUrl());
        appVersion.setIsForceUpdate(request.getIsForceUpdate());
        appVersion.setMinSupportedVersionCode(request.getMinSupportedVersionCode());
        appVersion.setCreateTime(new Date());
        appVersion.setUpdateTime(new Date());

        appVersionRepository.save(appVersion);
        return Result.ok(appVersion);
    }
}
