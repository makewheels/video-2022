package com.github.makewheels.video2022.utils;

import org.apache.commons.beanutils.BeanUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RequestUtil {

    /**
     * 根据url的参数，转为DTO对象
     */
    public static <T> T requestToBean(HttpServletRequest request, Class<T> clazz) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> simpleHashMap = new HashMap<>(parameterMap.size());
        Set<String> keySet = parameterMap.keySet();
        for (String key : keySet) {
            String value = parameterMap.get(key)[0];
            simpleHashMap.put(key, value);
        }
        T t = null;
        try {
            t = clazz.getConstructor().newInstance();
            BeanUtils.populate(t, simpleHashMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return t;
    }

}
