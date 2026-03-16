package com.github.makewheels.video2022.developer.controller;

import com.github.makewheels.video2022.developer.dto.CreateDeveloperAppRequest;
import com.github.makewheels.video2022.developer.dto.UpdateDeveloperAppRequest;
import com.github.makewheels.video2022.developer.entity.DeveloperApp;
import com.github.makewheels.video2022.developer.service.DeveloperAppService;
import com.github.makewheels.video2022.developer.service.DeveloperWebhookService;
import com.github.makewheels.video2022.system.response.Result;
import com.github.makewheels.video2022.user.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("developer/app")
@Slf4j
@Tag(name = "DeveloperApp", description = "开发者应用管理")
public class DeveloperAppController {
    @Resource
    private DeveloperAppService developerAppService;

    @Resource
    private DeveloperWebhookService developerWebhookService;

    @Operation(summary = "创建开发者应用")
    @PostMapping("create")
    public Result<DeveloperApp> create(@RequestBody CreateDeveloperAppRequest request) {
        String userId = UserHolder.getUserId();
        DeveloperApp app = developerAppService.createApp(userId, request.getAppName());
        return Result.ok(app);
    }

    @Operation(summary = "获取应用列表")
    @GetMapping("list")
    public Result<List<DeveloperApp>> list() {
        String userId = UserHolder.getUserId();
        List<DeveloperApp> apps = developerAppService.listByUserId(userId);
        return Result.ok(apps);
    }

    @Operation(summary = "更新应用信息")
    @PutMapping("{appId}/update")
    public Result<DeveloperApp> update(
            @PathVariable String appId,
            @RequestBody UpdateDeveloperAppRequest request) {
        String userId = UserHolder.getUserId();
        DeveloperApp app = developerAppService.updateApp(userId, appId, request);
        return Result.ok(app);
    }

    @Operation(summary = "删除应用")
    @DeleteMapping("{appId}/delete")
    public Result<Void> delete(@PathVariable String appId) {
        String userId = UserHolder.getUserId();
        developerAppService.deleteApp(userId, appId);
        return Result.ok();
    }

    @Operation(summary = "重新生成 appSecret")
    @PostMapping("{appId}/regenerateSecret")
    public Result<DeveloperApp> regenerateSecret(@PathVariable String appId) {
        String userId = UserHolder.getUserId();
        DeveloperApp app = developerAppService.regenerateSecret(userId, appId);
        return Result.ok(app);
    }

    @Operation(summary = "发送测试 Webhook 事件")
    @PostMapping("{appId}/testWebhook")
    public Result<Void> testWebhook(@PathVariable String appId) {
        String userId = UserHolder.getUserId();
        DeveloperApp app = developerAppService.getByAppId(appId);
        if (app == null) {
            return Result.error("App not found");
        }
        if (!userId.equals(app.getUserId())) {
            return Result.error("App does not belong to this user");
        }
        developerWebhookService.sendWebhookEvent(appId, "test.ping",
                java.util.Map.of("message", "This is a test webhook event"));
        return Result.ok("Test webhook sent");
    }
}
