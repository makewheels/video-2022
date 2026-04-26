package com.github.makewheels.video2022.openapi;

import com.github.makewheels.video2022.BaseIntegrationTest;
import com.github.makewheels.video2022.openapi.oauth.dto.DeveloperStatsResponse;
import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperConsoleService;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import com.github.makewheels.video2022.openapi.ratelimit.RateLimitRecord;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookDelivery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeveloperConsoleServiceTest extends BaseIntegrationTest {

    @Autowired
    private DeveloperService developerService;

    @Autowired
    private OAuthAppService oAuthAppService;

    @Autowired
    private DeveloperConsoleService developerConsoleService;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void getStats_aggregatesAppsWebhooksAndUsage() {
        Developer developer = developerService.register("stats@example.com", "password123", "统计开发者", null);
        OAuthApp app1 = oAuthAppService.createApp(
                developer.getId(), "应用一", "desc", List.of("https://a.example.com/callback"), List.of("video:read")
        );
        OAuthApp app2 = oAuthAppService.createApp(
                developer.getId(), "应用二", "desc", List.of("https://b.example.com/callback"), List.of("video:write")
        );

        WebhookConfig webhook1 = new WebhookConfig();
        webhook1.setAppId(app1.getId());
        webhook1.setUrl("https://example.com/webhook/1");
        webhook1.setEvents(List.of("video.ready"));
        webhook1.setStatus("active");
        webhook1.setSecret("secret-1");
        webhook1.setCreateTime(new Date());
        webhook1.setUpdateTime(new Date());
        mongoTemplate.save(webhook1);

        WebhookConfig webhook2 = new WebhookConfig();
        webhook2.setAppId(app2.getId());
        webhook2.setUrl("https://example.com/webhook/2");
        webhook2.setEvents(List.of("video.deleted"));
        webhook2.setStatus("active");
        webhook2.setSecret("secret-2");
        webhook2.setCreateTime(new Date());
        webhook2.setUpdateTime(new Date());
        mongoTemplate.save(webhook2);

        RateLimitRecord record1 = new RateLimitRecord();
        record1.setAppId(app1.getId());
        record1.setTotalRequests(12);
        mongoTemplate.save(record1);

        RateLimitRecord record2 = new RateLimitRecord();
        record2.setAppId(app2.getId());
        record2.setTotalRequests(30);
        mongoTemplate.save(record2);

        WebhookDelivery delivery1 = new WebhookDelivery();
        delivery1.setWebhookConfigId(webhook1.getId());
        delivery1.setStatus("SUCCESS");
        delivery1.setCreateTime(new Date());
        mongoTemplate.save(delivery1);

        WebhookDelivery delivery2 = new WebhookDelivery();
        delivery2.setWebhookConfigId(webhook1.getId());
        delivery2.setStatus("SUCCESS");
        delivery2.setCreateTime(new Date());
        mongoTemplate.save(delivery2);

        WebhookDelivery delivery3 = new WebhookDelivery();
        delivery3.setWebhookConfigId(webhook2.getId());
        delivery3.setStatus("FAILED");
        delivery3.setCreateTime(new Date());
        mongoTemplate.save(delivery3);

        DeveloperStatsResponse stats = developerConsoleService.getStats(developer.getId());

        assertEquals(2, stats.getAppCount());
        assertEquals(2, stats.getWebhookCount());
        assertEquals(42L, stats.getTotalApiRequests());
        assertEquals(3L, stats.getWebhookDeliveryCount());
    }

    @Test
    void getStats_withoutApps_returnsZeroes() {
        Developer developer = developerService.register("empty@example.com", "password123", "空开发者", null);

        DeveloperStatsResponse stats = developerConsoleService.getStats(developer.getId());

        assertEquals(0, stats.getAppCount());
        assertEquals(0, stats.getWebhookCount());
        assertEquals(0L, stats.getTotalApiRequests());
        assertEquals(0L, stats.getWebhookDeliveryCount());
    }
}
