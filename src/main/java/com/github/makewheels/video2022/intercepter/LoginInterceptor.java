package com.github.makewheels.video2022.intercepter;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.user.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private UserServiceClient userServiceClient;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("token");
        User user = userServiceClient.getUserByRequest(request);
        log.debug("token = {}, user = {}", token, JSON.toJSONString(user));
        if (user == null) {
            response.setStatus(403);
            return false;
        } else {
            response.setStatus(200);
            return true;
        }
    }

}
