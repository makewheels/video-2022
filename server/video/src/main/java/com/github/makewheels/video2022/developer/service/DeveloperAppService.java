package com.github.makewheels.video2022.developer.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.makewheels.video2022.developer.dto.UpdateDeveloperAppRequest;
import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import com.github.makewheels.video2022.developer.repository.DeveloperAppRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class DeveloperAppService {
    @Resource
    private DeveloperAppRepository developerAppRepository;

    public DeveloperApp createApp(String userId, String appName) {
        DeveloperApp app = new DeveloperApp();
        app.setUserId(userId);
        app.setAppName(appName);
        app.setAppId(IdUtil.fastSimpleUUID());
        app.setAppSecret(RandomUtil.randomString(32));
        app.setWebhookSecret(RandomUtil.randomString(32));
        return developerAppRepository.save(app);
    }

    public List<DeveloperApp> listByUserId(String userId) {
        return developerAppRepository.findByUserId(userId);
    }

    public DeveloperApp getByAppId(String appId) {
        return developerAppRepository.findByAppId(appId);
    }

    public DeveloperApp updateApp(String userId, String appId, UpdateDeveloperAppRequest request) {
        DeveloperApp app = developerAppRepository.findByAppId(appId);
        if (app == null) {
            throw new RuntimeException("App not found");
        }
        if (!userId.equals(app.getUserId())) {
            throw new RuntimeException("App does not belong to this user");
        }
        if (request.getWebhookUrl() != null) {
            app.setWebhookUrl(request.getWebhookUrl());
        }
        if (request.getWebhookEvents() != null) {
            app.setWebhookEvents(request.getWebhookEvents());
        }
        app.setUpdatedAt(new Date());
        return developerAppRepository.save(app);
    }

    public void deleteApp(String userId, String appId) {
        DeveloperApp app = developerAppRepository.findByAppId(appId);
        if (app == null) {
            throw new RuntimeException("App not found");
        }
        if (!userId.equals(app.getUserId())) {
            throw new RuntimeException("App does not belong to this user");
        }
        developerAppRepository.deleteById(app.getId());
    }

    public DeveloperApp regenerateSecret(String userId, String appId) {
        DeveloperApp app = developerAppRepository.findByAppId(appId);
        if (app == null) {
            throw new RuntimeException("App not found");
        }
        if (!userId.equals(app.getUserId())) {
            throw new RuntimeException("App does not belong to this user");
        }
        app.setAppSecret(RandomUtil.randomString(32));
        app.setUpdatedAt(new Date());
        return developerAppRepository.save(app);
    }
}
