package com.github.makewheels.video2022.context;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.apache.commons.beanutils.BeanUtils;

import javax.servlet.http.HttpServletRequest;
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
    public static <T> T toDTO(HttpServletRequest request, Class<T> clazz) {
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


}
