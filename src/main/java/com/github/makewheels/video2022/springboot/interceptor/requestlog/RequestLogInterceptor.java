package com.github.makewheels.video2022.springboot.interceptor.requestlog;

import com.github.makewheels.video2022.springboot.interceptor.InterceptorOrder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 记录请求日志
 */
@Component
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor, Ordered {
    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                @NotNull Object handler, Exception ex) {

    }

    @Override
    public int getOrder() {
        return InterceptorOrder.REQUEST_LOG;
    }
}
