package com.github.makewheels.video2022.openapi.oauth;

import com.github.makewheels.video2022.openapi.oauth.dto.DeveloperStatsResponse;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperConsoleService;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import com.github.makewheels.video2022.openapi.v1.dto.RegisterWebhookApiRequest;
import com.github.makewheels.video2022.openapi.webhook.entity.WebhookConfig;
import com.github.makewheels.video2022.openapi.webhook.repository.WebhookConfigRepository;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("developer")
@Slf4j
@Tag(name = "Developer Console", description = "开发者控制台统计与 Webhook 管理")
public class DeveloperConsoleController {
    @Resource
    private DeveloperService developerService;
    @Resource
    private OAuthAppService oAuthAppService;
    @Resource
    private DeveloperConsoleService developerConsoleService;
    @Resource
    private WebhookConfigRepository webhookConfigRepository;

    @Operation(summary = "获取开发者控制台统计")
    @GetMapping("stats")
    public Result<DeveloperStatsResponse> stats(HttpServletRequest request) {
        String developerId = extractDeveloperId(request);
        if (developerId == null) {
            return Result.error("Unauthorized: invalid or missing developer token");
        }
        return Result.ok(developerConsoleService.getStats(developerId));
    }

    @Operation(summary = "获取应用的 Webhook 列表")
    @GetMapping("apps/{appId}/webhooks")
    public Result<List<WebhookConfig>> listWebhooks(@PathVariable String appId, HttpServletRequest request) {
        OAuthApp app = requireOwnedApp(request, appId);
        if (app == null) {
            return Result.error("App not found or access denied");
        }
        return Result.ok(webhookConfigRepository.findByAppId(app.getId()));
    }

    @Operation(summary = "创建应用的 Webhook")
    @PostMapping("apps/{appId}/webhooks")
    public Result<WebhookConfig> createWebhook(@PathVariable String appId,
                                               @RequestBody RegisterWebhookApiRequest request,
                                               HttpServletRequest servletRequest) {
        OAuthApp app = requireOwnedApp(servletRequest, appId);
        if (app == null) {
            return Result.error("App not found or access denied");
        }

        WebhookConfig config = new WebhookConfig();
        config.setAppId(app.getId());
        config.setUrl(request.getUrl());
        config.setEvents(request.getEvents());
        config.setSecret(UUID.randomUUID().toString().replace("-", ""));
        config.setStatus("active");
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        webhookConfigRepository.save(config);
        log.info("开发者控制台创建Webhook: appId={}, url={}", app.getId(), request.getUrl());
        return Result.ok(config);
    }

    @Operation(summary = "删除应用的 Webhook")
    @DeleteMapping("apps/{appId}/webhooks/{webhookId}")
    public Result<Void> deleteWebhook(@PathVariable String appId,
                                      @PathVariable String webhookId,
                                      HttpServletRequest request) {
        OAuthApp app = requireOwnedApp(request, appId);
        if (app == null) {
            return Result.error("App not found or access denied");
        }
        WebhookConfig config = webhookConfigRepository.getById(webhookId);
        if (config == null || !app.getId().equals(config.getAppId())) {
            return Result.error("Webhook not found");
        }
        webhookConfigRepository.deleteById(webhookId);
        log.info("开发者控制台删除Webhook: appId={}, webhookId={}", app.getId(), webhookId);
        return Result.ok();
    }

    private OAuthApp requireOwnedApp(HttpServletRequest request, String appId) {
        String developerId = extractDeveloperId(request);
        if (developerId == null) {
            return null;
        }
        OAuthApp app = oAuthAppService.getById(appId);
        if (app == null) {
            return null;
        }
        return developerId.equals(app.getDeveloperId()) ? app : null;
    }

    private String extractDeveloperId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return developerService.validateJwt(authHeader.substring(7));
    }
}
