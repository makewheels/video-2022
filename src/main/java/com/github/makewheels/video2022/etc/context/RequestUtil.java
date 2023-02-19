package com.github.makewheels.video2022.etc.context;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RequestUtil {
    private static <T> T mapToBean(Map<String, String> map, Class<T> clazz) {
        T t = null;
        try {
            t = clazz.getConstructor().newInstance();
            BeanUtils.populate(t, map);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return t;
    }

    /**
     * 根据url的参数，转为DTO对象
     */
    public static <T> T toDTO(Class<T> clazz) {
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

    public static HttpSession getSession() {
        return getRequest().getSession();
    }

    public static String getSessionId() {
        return getSession().getId();
    }
}
