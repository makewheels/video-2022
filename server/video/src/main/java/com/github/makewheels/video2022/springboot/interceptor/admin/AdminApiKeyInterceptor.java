package com.github.makewheels.video2022.springboot.interceptor.admin;

import com.github.makewheels.video2022.springboot.interceptor.InterceptorOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 管理接口鉴权拦截器
 * 通过请求头 X-Admin-Api-Key 验证
 */
@Slf4j
@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor, Ordered {

    @Value("${admin.api-key:}")
    private String adminApiKey;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response,
            Object handler) throws IOException {
        String requestKey = request.getHeader("X-Admin-Api-Key");

        if (adminApiKey.isEmpty()) {
            log.warn("ADMIN_API_KEY 未配置，拒绝管理接口请求");
            sendForbidden(response, "管理接口未配置密钥");
            return false;
        }

        if (!adminApiKey.equals(requestKey)) {
            log.warn("管理接口鉴权失败，IP: {}", request.getRemoteAddr());
            sendForbidden(response, "鉴权失败");
            return false;
        }

        return true;
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.ADMIN_API_KEY;
    }
}
