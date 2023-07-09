package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.etc.sign;

import cn.hutool.http.HttpUtil;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * 加签服务
 */
@Service
public class SignService {
    public String sign(String data) {
        return null;
    }

    public boolean verify(HttpServletRequest request) {
        // 按key排序
        Map<String, String> decodeParamMap = HttpUtil.decodeParamMap(request.getQueryString(), StandardCharsets.UTF_8);
        decodeParamMap.remove("sign");
        Map<String, String> treeMap = new TreeMap<>(decodeParamMap);
        String params = HttpUtil.toParams(treeMap);

        return false;
    }

    public boolean verify(String data, String sign) {
        return false;
    }
}
