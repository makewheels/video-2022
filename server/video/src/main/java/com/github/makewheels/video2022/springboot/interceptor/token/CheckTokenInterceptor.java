package com.github.makewheels.video2022.springboot.interceptor.token;

import com.github.makewheels.video2022.springboot.interceptor.InterceptorOrder;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 校验需要登录的接口
 */
@Slf4j
@Component
public class CheckTokenInterceptor implements HandlerInterceptor, Ordered {
    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response,
            Object handler) throws IOException {
        //通过token获取User
        User user = userService.getUserByRequest(request);

        //找到了用户，校验通过
        if (user != null) {
            response.setStatus(200);
            return true;
        }

        //token无效，返回401 JSON响应，由前端处理跳转
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
        return false;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception e) {
        UserHolder.remove();
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.CHECK_TOKEN;
    }
}
