package com.github.makewheels.video2022.openapi.ratelimit;

import com.github.makewheels.video2022.openapi.oauth.OAuthContext;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Jakarta Servlet Filter 实现 API 限流。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    @Resource
    private RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key;
        boolean isAppAuth;

        String clientId = OAuthContext.getClientId();
        if (clientId != null) {
            key = clientId;
            isAppAuth = true;
        } else {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey != null && !apiKey.isBlank()) {
                key = apiKey;
                isAppAuth = true;
            } else {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    key = authHeader.substring(7);
                    isAppAuth = true;
                } else {
                    key = request.getRemoteAddr();
                    isAppAuth = false;
                }
            }
        }

        RateLimitResult result = rateLimitService.checkRateLimit(key, isAppAuth);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getMinuteLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getMinuteRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));
        response.setHeader("X-RateLimit-Daily-Limit", String.valueOf(result.getDayLimit()));
        response.setHeader("X-RateLimit-Daily-Remaining", String.valueOf(result.getDayRemaining()));

        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            JsonObject body = new JsonObject();
            body.addProperty("error", "rate_limit_exceeded");
            body.addProperty("message", "请求频率超限");
            body.addProperty("retryAfter", result.getRetryAfter());

            response.setHeader("Retry-After", String.valueOf(result.getRetryAfter()));
            response.getWriter().write(body.toString());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
