package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public class IpUtil {
    public static JSONObject queryIp(String ip) {
        JSONObject body = new JSONObject();
        body.put("ip", ip);
        String json = HttpUtil.post("http://81.70.242.253:5031/ip/query", body.toJSONString());
        return JSON.parseObject(json);
    }
}
