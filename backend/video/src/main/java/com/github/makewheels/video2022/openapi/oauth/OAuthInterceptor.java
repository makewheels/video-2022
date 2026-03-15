package com.github.makewheels.video2022.openapi.oauth;

import com.github.makewheels.video2022.openapi.oauth.entity.OAuthApp;
import com.github.makewheels.video2022.openapi.oauth.entity.OAuthToken;
import com.github.makewheels.video2022.openapi.oauth.repository.OAuthAppRepository;
import com.github.makewheels.video2022.openapi.oauth.service.OAuthTokenService;
import com.github.makewheels.video2022.springboot.interceptor.InterceptorOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class OAuthInterceptor implements HandlerInterceptor, Ordered {
    @Resource
    private OAuthTokenService oAuthTokenService;

    @Resource
    private OAuthAppRepository oAuthAppRepository;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response,
            Object handler) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header");
            return false;
        }

        String accessToken = authHeader.substring(7);
        OAuthToken token = oAuthTokenService.validateAccessToken(accessToken);
        if (token == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired access token");
            return false;
        }

        OAuthApp app = oAuthAppRepository.getById(token.getAppId());
        if (app == null || !"active".equals(app.getStatus())) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "OAuth application is not active");
            return false;
        }

        OAuthContext.setCurrentApp(app);
        OAuthContext.setCurrentUserId(token.getUserId());
        OAuthContext.setCurrentScopes(token.getScopes());

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        OAuthContext.remove();
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.OAUTH;
    }
}
