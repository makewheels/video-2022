package com.github.makewheels.video2022.openapi.ratelimit;

import com.github.makewheels.video2022.openapi.oauth.OAuthContext;
import com.github.makewheels.video2022.system.response.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/rateLimit")
public class RateLimitController {

    @Resource
    private RateLimitService rateLimitService;

    @GetMapping("status")
    public Result<RateLimitResult> status(HttpServletRequest request) {
        String clientId = OAuthContext.getClientId();
        boolean isAppAuth;
        String key;

        if (clientId != null) {
            key = clientId;
            isAppAuth = true;
        } else {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isBlank()) {
                key = apiKey;
                isAppAuth = true;
            } else {
                key = request.getRemoteAddr();
                isAppAuth = false;
            }
        }

        RateLimitResult result = rateLimitService.getStatus(key, isAppAuth);
        return Result.ok(result);
    }
}
