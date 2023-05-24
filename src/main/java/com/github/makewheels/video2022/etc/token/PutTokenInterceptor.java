package com.github.makewheels.video2022.etc.token;

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

/**
 * 把token放到ThreadLocal
 */
@Slf4j
@Component
public class PutTokenInterceptor implements HandlerInterceptor, Ordered {
    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull Object handler) {
        //通过token获取User，放入userHolder
        User user = userService.getUserByRequest(request);
        if (user != null) {
            UserHolder.set(user);
        }
        return true;
    }

    @Override
    public int getOrder() {
        return 1001;
    }
}
