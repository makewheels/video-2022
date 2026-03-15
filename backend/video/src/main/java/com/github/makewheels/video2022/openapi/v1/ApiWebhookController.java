package com.github.makewheels.video2022.openapi.v1;

import com.github.makewheels.video2022.openapi.oauth.OAuthContext;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.v1.dto.RegisterWebhookApiRequest;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookConfigRepository;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Tag(name = "Webhooks", description = "Webhook管理")
@RestController
@RequestMapping("/api/v1/webhooks")
@Slf4j
public class ApiWebhookController {
    @Resource
    private WebhookConfigRepository webhookConfigRepository;

    private String requireAppId() {
        OAuthApp app = OAuthContext.getCurrentApp();
        if (app == null) {
            throw new IllegalStateException("OAuth app context not found");
        }
        return app.getId();
    }

    @Operation(summary = "注册Webhook")
    @PostMapping
    public Result<WebhookConfig> registerWebhook(@RequestBody RegisterWebhookApiRequest request) {
        String appId = requireAppId();
        WebhookConfig config = new WebhookConfig();
        config.setAppId(appId);
        config.setUrl(request.getUrl());
        config.setEvents(request.getEvents());
        config.setSecret(UUID.randomUUID().toString().replace("-", ""));
        config.setStatus("active");
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        webhookConfigRepository.save(config);
        log.info("注册Webhook: appId={}, url={}", appId, request.getUrl());
        return Result.ok(config);
    }

    @Operation(summary = "获取Webhook列表")
    @GetMapping
    public Result<List<WebhookConfig>> listWebhooks() {
        String appId = requireAppId();
        List<WebhookConfig> configs = webhookConfigRepository.findByAppId(appId);
        return Result.ok(configs);
    }

    @Operation(summary = "删除Webhook")
    @DeleteMapping("/{id}")
    public Result<Void> deleteWebhook(@PathVariable String id) {
        requireAppId();
        webhookConfigRepository.deleteById(id);
        log.info("删除Webhook: id={}", id);
        return Result.ok();
    }
}
