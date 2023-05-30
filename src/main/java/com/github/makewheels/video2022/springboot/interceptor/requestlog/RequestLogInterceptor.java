package com.github.makewheels.video2022.springboot.interceptor.requestlog;

import com.github.makewheels.video2022.springboot.interceptor.InterceptorOrder;
import com.github.makewheels.video2022.system.context.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * 记录请求日志
 */
@Component
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor, Ordered {
    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public boolean preHandle(
            HttpServletRequest servletRequest, @NotNull HttpServletResponse servletResponse,
            @NotNull Object handler) {
        // 创建 RequestLog 对象，设置请求开始时间
        RequestLog requestLog = new RequestLog();
        requestLog.setStartTime(new Date());

        Request request = new Request();
        request.setUrl(servletRequest.getRequestURL().toString());
        request.setPath(servletRequest.getRequestURI());
        request.setMethod(servletRequest.getMethod());
        request.setQueryString(servletRequest.getQueryString());
        request.setHeaderMap(RequestUtil.getHeaderMap());
        request.setIp(RequestUtil.getIp());
        request.setUserAgent(RequestUtil.getUserAgent());

        requestLog.setRequest(request);

        // 将 RequestLog 对象保存到 RequestLogContext 中
        RequestLogContext.setRequestLog(requestLog);
        return true;
    }

    @Override
    public void postHandle(
            @NotNull HttpServletRequest servletRequest, @NotNull HttpServletResponse servletResponse,
            @NotNull Object handler, ModelAndView modelAndView) {
        RequestLog requestLog = RequestLogContext.getRequestLog();
        Response response = new Response();
        response.setHttpStatus(servletResponse.getStatus());
        // TODO 不知道怎么获取响应体

        // 设置请求结束时间
        requestLog.setEndTime(new Date());
        requestLog.setResponse(response);

        // 计算请求耗时
        long cost = requestLog.getEndTime().getTime() - requestLog.getStartTime().getTime();
        requestLog.setTimeCost(cost);
    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest servletRequest, @NotNull HttpServletResponse servletResponse,
            @NotNull Object handler, Exception ex) {
        // 保存到数据库
        mongoTemplate.save(RequestLogContext.getRequestLog());
        // 释放ThreadLocal
        RequestLogContext.removeRequestLog();
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.REQUEST_LOG;
    }
}
