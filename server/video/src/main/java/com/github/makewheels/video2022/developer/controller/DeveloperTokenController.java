package com.github.makewheels.video2022.developer.controller;

import com.github.makewheels.video2022.developer.dto.CreateTokenRequest;
import com.github.makewheels.video2022.developer.dto.VerifyTokenRequest;
import com.github.makewheels.video2022.developer.service.DeveloperTokenService;
import com.github.makewheels.video2022.system.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("developer/token")
@Slf4j
@Tag(name = "DeveloperToken", description = "开发者 JWT 令牌管理")
public class DeveloperTokenController {
    @Resource
    private DeveloperTokenService developerTokenService;

    @Operation(summary = "签发 JWT 令牌", description = "使用 appId + appSecret 换取 JWT 令牌，有效期24小时")
    @PostMapping("create")
    public Result<Map<String, Object>> create(@RequestBody CreateTokenRequest request) {
        String token = developerTokenService.createToken(request.getAppId(), request.getAppSecret());
        return Result.ok(Map.of("token", token));
    }

    @Operation(summary = "验证 JWT 令牌", description = "验证 JWT 令牌是否有效，返回令牌中的信息")
    @PostMapping("verify")
    public Result<Map<String, Object>> verify(@RequestBody VerifyTokenRequest request) {
        Map<String, Object> claims = developerTokenService.verifyToken(request.getToken());
        if (claims == null) {
            return Result.error("Invalid or expired token");
        }
        return Result.ok(claims);
    }
}
