package com.github.makewheels.video2022.openapi.oauth;

import com.github.makewheels.video2022.openapi.oauth.dto.*;
import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.service.DeveloperService;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthAppService;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("developer")
@Slf4j
@Tag(name = "Developer", description = "开发者账号管理")
public class DeveloperController {
    @Resource
    private DeveloperService developerService;

    @Resource
    private OAuthAppService oAuthAppService;

    /**
     * 开发者注册，注册成功后自动登录返回JWT
     */
    @Operation(summary = "开发者注册", description = "注册新的开发者账号，注册成功后自动登录并返回 JWT 令牌。")
    @PostMapping("register")
    public Result<DeveloperLoginResponse> register(@RequestBody DeveloperRegisterRequest request) {
        Developer developer = developerService.register(
                request.getEmail(), request.getPassword(),
                request.getName(), request.getCompany());

        String token = developerService.login(request.getEmail(), request.getPassword());

        DeveloperLoginResponse response = new DeveloperLoginResponse();
        response.setToken(token);
        response.setDeveloperId(developer.getId());
        response.setEmail(developer.getEmail());
        response.setName(developer.getName());
        return Result.ok(response);
    }

    /**
     * 开发者登录，返回JWT
     */
    @Operation(summary = "开发者登录", description = "使用邮箱和密码登录，返回 JWT 令牌。令牌有效期为24小时。")
    @PostMapping("login")
    public Result<DeveloperLoginResponse> login(@RequestBody DeveloperLoginRequest request) {
        String token = developerService.login(request.getEmail(), request.getPassword());
        Developer developer = developerService.findByEmail(request.getEmail());

        DeveloperLoginResponse response = new DeveloperLoginResponse();
        response.setToken(token);
        response.setDeveloperId(developer.getId());
        response.setEmail(developer.getEmail());
        response.setName(developer.getName());
        return Result.ok(response);
    }

    /**
     * 获取当前开发者信息（需要JWT）
     */
    @Operation(summary = "获取当前开发者信息", description = "通过 JWT 令牌获取当前登录的开发者信息。需要在 Authorization 头中携带 Bearer token。")
    @GetMapping("me")
    public Result<DeveloperVO> me(HttpServletRequest request) {
        String developerId = extractDeveloperId(request);
        if (developerId == null) {
            return Result.error("Unauthorized: invalid or missing developer token");
        }
        Developer developer = developerService.getById(developerId);
        if (developer == null) {
            return Result.error("Developer not found");
        }
        return Result.ok(toDeveloperVO(developer));
    }

    /**
     * 创建OAuth应用（需要JWT）
     */
    @Operation(summary = "创建 OAuth 应用", description = "为当前开发者创建一个新的 OAuth 应用，获取 client_id 和 client_secret 用于 API 调用。")
    @PostMapping("apps")
    public Result<CreateOAuthAppResponse> createApp(
            @RequestBody CreateOAuthAppRequest request,
            HttpServletRequest servletRequest) {
        String developerId = extractDeveloperId(servletRequest);
        if (developerId == null) {
            return Result.error("Unauthorized: invalid or missing developer token");
        }

        OAuthApp app = oAuthAppService.createApp(
                developerId, request.getName(), request.getDescription(),
                request.getRedirectUris(), request.getScopes());

        CreateOAuthAppResponse response = new CreateOAuthAppResponse();
        response.setAppId(app.getId());
        response.setClientId(app.getClientId());
        response.setClientSecret(app.getClientSecretHash());
        response.setName(app.getName());
        response.setDescription(app.getDescription());
        response.setRedirectUris(app.getRedirectUris());
        response.setScopes(app.getScopes());
        response.setRateLimitTier(app.getRateLimitTier());
        response.setStatus(app.getStatus());
        response.setCreateTime(app.getCreateTime());
        return Result.ok(response);
    }

    /**
     * 获取当前开发者的OAuth应用列表（需要JWT）
     */
    @Operation(summary = "获取应用列表", description = "获取当前开发者创建的所有 OAuth 应用列表。")
    @GetMapping("apps")
    public Result<List<OAuthAppVO>> listApps(HttpServletRequest request) {
        String developerId = extractDeveloperId(request);
        if (developerId == null) {
            return Result.error("Unauthorized: invalid or missing developer token");
        }

        List<OAuthApp> apps = oAuthAppService.getAppsByDeveloperId(developerId);
        List<OAuthAppVO> voList = apps.stream().map(app -> {
            OAuthAppVO vo = new OAuthAppVO();
            BeanUtils.copyProperties(app, vo);
            return vo;
        }).collect(Collectors.toList());
        return Result.ok(voList);
    }

    /**
     * 删除OAuth应用（需要JWT）
     */
    @Operation(summary = "删除 OAuth 应用", description = "删除指定的 OAuth 应用，删除后该应用的所有令牌将失效。")
    @DeleteMapping("apps/{appId}")
    public Result<Void> deleteApp(
            @PathVariable String appId,
            HttpServletRequest request) {
        String developerId = extractDeveloperId(request);
        if (developerId == null) {
            return Result.error("Unauthorized: invalid or missing developer token");
        }
        oAuthAppService.deleteApp(developerId, appId);
        return Result.ok();
    }

    private String extractDeveloperId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return developerService.validateJwt(authHeader.substring(7));
    }

    private DeveloperVO toDeveloperVO(Developer developer) {
        DeveloperVO vo = new DeveloperVO();
        BeanUtils.copyProperties(developer, vo);
        return vo;
    }
}
