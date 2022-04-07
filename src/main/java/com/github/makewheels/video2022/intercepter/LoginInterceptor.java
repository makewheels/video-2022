package com.github.makewheels.video2022.intercepter;

import com.alibaba.fastjson.JSON;
import com.github.makewheels.usermicroservice2022.User;
import com.github.makewheels.video2022.user.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    private UserServiceClient userServiceClient;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException, URISyntaxException {
        String token = request.getHeader("token");
        User user = userServiceClient.getUserByRequest(request);
        log.debug("token = {}, user = {}", token, JSON.toJSONString(user));
        if (user == null) {
            response.setStatus(403);

            URI uri = new URI(request.getRequestURL().toString());
            String target = uri.getScheme() + "://" + uri.getHost() + ":5021/user-micro-service-2022/login.html";
            response.sendRedirect(target);
            return false;
        } else {
            response.setStatus(200);
            return true;
        }
    }

}
