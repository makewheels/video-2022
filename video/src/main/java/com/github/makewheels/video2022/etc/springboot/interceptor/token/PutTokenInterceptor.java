package com.github.makewheels.video2022.etc.springboot.interceptor.token;

import com.github.makewheels.video2022.etc.springboot.interceptor.InterceptorOrder;
import com.github.makewheels.video2022.user.UserHolder;
import com.github.makewheels.video2022.user.UserService;
import com.github.makewheels.video2022.user.bean.User;
import lombok.extern.slf4j.Slf4j;
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
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        //通过token获取User，放入userHolder
        User user = userService.getUserByRequest(request);
        if (user != null) {
            UserHolder.set(user);
        }
        return true;
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.PUT_TOKEN;
    }
}
