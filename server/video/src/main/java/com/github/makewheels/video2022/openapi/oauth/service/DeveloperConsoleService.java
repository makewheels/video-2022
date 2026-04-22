package com.github.makewheels.video2022.openapi.oauth.service;

import com.github.makewheels.video2022.openapi.oauth.dto.DeveloperStatsResponse;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.ratelimit.RateLimitRecord;
import com.github.makewheels.video2022.openapi.ratelimit.RateLimitService;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookConfigRepository;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookDeliveryRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class DeveloperConsoleService {
    @Resource
    private OAuthAppService oAuthAppService;
    @Resource
    private WebhookConfigRepository webhookConfigRepository;
    @Resource
    private WebhookDeliveryRepository webhookDeliveryRepository;
    @Resource
    private RateLimitService rateLimitService;

    public DeveloperStatsResponse getStats(String developerId) {
        List<OAuthApp> apps = oAuthAppService.getAppsByDeveloperId(developerId);
        List<String> appIds = apps.stream().map(OAuthApp::getId).toList();
        List<WebhookConfig> webhooks = webhookConfigRepository.findByAppIds(appIds);
        List<String> webhookIds = webhooks.stream().map(WebhookConfig::getId).toList();

        long totalApiRequests = apps.stream()
                .map(OAuthApp::getId)
                .map(rateLimitService::getRecord)
                .filter(record -> record != null)
                .mapToLong(RateLimitRecord::getTotalRequests)
                .sum();

        DeveloperStatsResponse response = new DeveloperStatsResponse();
        response.setAppCount(apps.size());
        response.setWebhookCount(webhooks.size());
        response.setTotalApiRequests(totalApiRequests);
        response.setWebhookDeliveryCount(webhookDeliveryRepository.countByWebhookConfigIds(webhookIds));
        return response;
    }
}
