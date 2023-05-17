package com.github.makewheels.video2022.interceptor;

import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
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
    private UserService userService;
    @Value("${server.port}")
    private Integer serverPort;

    public LoginInterceptor() {
    }

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull Object handler) throws IOException, URISyntaxException {
        //通过token获取User
        User user = userService.getUserByRequest(request);

        //找到了用户，校验通过
        if (user != null) {
            //放入userHolder
            UserHolder.set(user);
            response.setStatus(200);
            return true;
        }

        //如果不通过，让他回登录页
        response.setStatus(403);
        String target = request.getRequestURL().toString();
        URI uri = new URI(target);
        response.sendRedirect(uri.getScheme() + "://" + uri.getHost() + ":" + serverPort
                + "/login.html?target=" + target);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception e) {
        UserHolder.remove();
    }
}
