package com.github.makewheels.video2022.openapi.ratelimit;

import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.openapi.oauth.OAuthContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    @Resource
    private RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String clientId = OAuthContext.getClientId();
        if (clientId == null) {
            // No OAuth client context — use IP as fallback identifier
            clientId = request.getRemoteAddr();
        }

        String tier = OAuthContext.getTier();
        RateLimitResult result = rateLimitService.checkRateLimit(clientId, tier);

        // Always set rate-limit response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));

        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            JSONObject body = new JSONObject();
            body.put("code", HttpStatus.TOO_MANY_REQUESTS.value());
            body.put("message", "Rate limit exceeded. Please retry after "
                    + result.getResetTime() + " epoch seconds.");
            body.put("limit", result.getLimit());
            body.put("remaining", result.getRemaining());
            body.put("resetTime", result.getResetTime());

            response.getWriter().write(body.toJSONString());
            return false;
        }
        return true;
    }
}
