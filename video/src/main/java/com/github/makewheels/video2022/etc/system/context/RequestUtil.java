package com.github.makewheels.video2022.etc.system.context;

import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RequestUtil {
    private static <T> T mapToBean(Map<String, String> map, Class<T> clazz) {
        T t = null;
        try {
            t = clazz.getConstructor().newInstance();
            BeanUtils.populate(t, map);
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        return t;
    }

    /**
     * 根据url的参数，转为DTO对象
     */
    private static <T> T toDTO(Class<T> clazz) {
        HttpServletRequest request = getRequest();
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> simpleHashMap = new HashMap<>(parameterMap.size());
        Set<String> keySet = parameterMap.keySet();
        for (String key : keySet) {
            String value = parameterMap.get(key)[0];
            simpleHashMap.put(key, value);
        }
        return mapToBean(simpleHashMap, clazz);
    }

    public static <T> T toDTO(String json, Class<T> clazz) {
        TypeReference<Map<String, String>> typeReference = new TypeReference<Map<String, String>>() {
        };
        Map<String, String> map = JSONObject.parseObject(json, typeReference);
        return mapToBean(map, clazz);
    }

    public static <T> T toDTO(JSONObject body, Class<T> clazz) {
        return toDTO(body.toJSONString(), clazz);
    }

    public static Context getContext() {
        return toDTO(Context.class);
    }

    /**
     * 获取本次请求request对象
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
     * 获取本次请求response对象
     */
    public static HttpServletResponse getResponse() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return ((ServletRequestAttributes) requestAttributes).getResponse();
    }

    /**
     * 通过servlet request获取请求头map
     */
    public static Map<String, Object> getHeaderMap() {
        HttpServletRequest request = getRequest();
        Map<String, Object> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            headerMap.put(name, value);
        }
        return headerMap;
    }

    public static HttpSession getSession() {
        return getRequest().getSession();
    }

    public static String getSessionId() {
        return getSession().getId();
    }

    public static String getHeader(String name) {
        return getRequest().getHeader(name);
    }

    public static String getUserAgent() {
        return getHeader("User-Agent");
    }

    public static String getIp() {
        return getRequest().getRemoteAddr();
    }

    /**
     * 获取请求体
     */
    public static String getRequestBody() {
        try {
            return IoUtil.readUtf8(getRequest().getInputStream());
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    /**
     * 获取请求body
     */
    public static JSONObject servletToRequestJSONObject(HttpServletRequest request) {
        try {
            return JSON.parseObject(IoUtil.readUtf8(request.getInputStream()));
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

}
