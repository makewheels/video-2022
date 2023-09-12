package com.github.makewheels.video2022.etc.springboot.interceptor.requestlog;

import com.github.makewheels.video2022.etc.springboot.interceptor.InterceptorOrder;
import com.github.makewheels.video2022.etc.system.context.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
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
            HttpServletRequest servletRequest, HttpServletResponse servletResponse,
            Object handler) {
        // 创建 RequestLog 对象，设置请求开始时间
        RequestLog requestLog = new RequestLog();
        requestLog.setStartTime(new Date());
        requestLog.setRequestPath(servletRequest.getRequestURI());

        Request request = new Request();
        request.setUrl(servletRequest.getRequestURL().toString());
        request.setPath(servletRequest.getRequestURI());
        request.setMethod(servletRequest.getMethod());
        request.setQueryString(servletRequest.getQueryString());
        request.setHeaderMap(RequestUtil.getHeaderMap());
        request.setIp(RequestUtil.getIp());
        request.setUserAgent(RequestUtil.getUserAgent());

        requestLog.setRequest(request);
        requestLog.setResponse(new Response());

        // 将 RequestLog 对象保存到 RequestLogContext 中
        RequestLogContext.setRequestLog(requestLog);
        return true;
    }

    @Override
    public void postHandle(
            @NotNull HttpServletRequest servletRequest, @NotNull HttpServletResponse servletResponse,
            @NotNull Object handler, ModelAndView modelAndView) {
        RequestLog requestLog = RequestLogContext.getRequestLog();
        // TODO 不知道怎么获取响应体

    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest servletRequest, @NotNull HttpServletResponse servletResponse,
            @NotNull Object handler, Exception ex) {

        RequestLog requestLog = RequestLogContext.getRequestLog();
        // 设置请求结束时间，计算请求耗时
        requestLog.setEndTime(new Date());
        long cost = requestLog.getEndTime().getTime() - requestLog.getStartTime().getTime();
        requestLog.setTimeCost(cost);

        Response response = requestLog.getResponse();
        response.setHttpStatus(servletResponse.getStatus());

        // 保存到数据库
        if (StringUtils.equalsAny(servletRequest.getRequestURI(),
                "/file/access", "/heartbeat/add")) {
            RequestLogContext.removeRequestLog();
            return;
        }
        mongoTemplate.save(requestLog);

        // 释放ThreadLocal
        RequestLogContext.removeRequestLog();
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.REQUEST_LOG;
    }
}
