package com.github.makewheels.video2022.intercepter;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class ClientIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //先查找cookie
        String clientId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (StringUtils.equals(cookie.getName(), "clientId")) {
                    clientId = cookie.getValue();
                }
            }
        }
        //如果没有这个cookie，那就设置
        if (StringUtils.isEmpty(clientId)) {
            clientId = IdUtil.getSnowflakeNextIdStr();
            Cookie cookie = new Cookie("clientId", clientId);
            log.info("没有找到cookie，设置为：" + clientId);
            cookie.setMaxAge(3 * 365 * 24 * 60 * 60);
            response.addCookie(cookie);
        }
        return true;
    }

}
