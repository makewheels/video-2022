package com.github.makewheels.video2022.system.token;

import com.github.makewheels.video2022.system.environment.EnvironmentService;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 校验需要登录的接口
 */
@Slf4j
@Component
public class CheckTokenInterceptor implements HandlerInterceptor, Ordered {
    @Resource
    private UserService userService;
    @Resource
    private EnvironmentService environmentService;

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull Object handler) throws IOException, URISyntaxException {
        //通过token获取User
        User user = userService.getUserByRequest(request);

        //找到了用户，校验通过
        if (user != null) {
            response.setStatus(200);
            return true;
        }

        //如果不通过，让他回登录页
        response.setStatus(403);
        String targetUrl = request.getRequestURL().toString();
        URI targetUri = new URI(targetUrl);
        response.sendRedirect(targetUri.getScheme() + "://" + targetUri.getHost()
                + ":" + environmentService.getServerPort() + "/login.html?target=" + targetUrl);
        return false;
    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull Object handler, Exception e) {
        UserHolder.remove();
    }

    @Override
    public int getOrder() {
        return 1002;
    }
}
