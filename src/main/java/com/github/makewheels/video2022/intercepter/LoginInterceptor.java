package com.github.makewheels.video2022.intercepter;

import com.github.makewheels.usermicroservice2022.user.User;
import com.github.makewheels.video2022.user.UserHolder;
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
        //token校验：既可以从header，也可以从url参数中获取
        String token = request.getHeader("token");
        String[] tokens = request.getParameterMap().get("token");
        User user = userServiceClient.getUserByRequest(request);
        if (tokens != null) {
            user = userServiceClient.getUserByToken(tokens[0]);
        }
//        log.debug("token = {}, user = {}", token, JSON.toJSONString(user));

        //找到了用户，校验通过
        if (user != null) {
            onLoginCheckPass(user);
            response.setStatus(200);
            return true;
        }

        //如果token校验不通过，让他放回登录页
        response.setStatus(403);
        String target = request.getRequestURL().toString();
        URI uri = new URI(target);
        response.sendRedirect(uri.getScheme() + "://" + uri.getHost()
                + ":5021/user-micro-service-2022/login.html?target=" + target);
        return false;
    }

    /**
     * 当用户拦截器校验通过时
     */
    private void onLoginCheckPass(User user) {
        //放入userHolder
        UserHolder.set(user);
    }

}
