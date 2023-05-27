package com.github.makewheels.video2022.file.md5;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.makewheels.video2022.system.environment.EnvironmentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用阿里云云函数，获取OSS文件的MD5
 */
@Service
public class Md5CfService {
    @Resource
    private EnvironmentService environmentService;

    /**
     * 示例请求：
     * {
     * "isCallbackEnable": false,
     * "keyList": [
     * "videos/64598761fab9341c488c38f7/646401931286d141d607bb22/original/646401931286d141d607bb22.mp4"
     * ]
     * }
     * <p>
     * 示例响应：
     * {
     * "aliyunRequestId": "1-64716c57-fcf9ffdd36d1572c6a21879c",
     * "objectList": [
     * {
     * "key": "videos/64598761fab9341c488c38f7/646401931286d141d607bb22/original/646401931286d141d607bb22.mp4",
     * "md5": "458a3b2992784ad3e3b7a511d25d5752"
     * }
     * ]
     * }
     */
    public String getOssObjectMd5(String key) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("isCallbackEnable", false);
        requestMap.put("keyList", new String[]{key});

        String url = environmentService.getAliyunCfUrlGetOssObjectMd5();
        String response = HttpUtil.post(url, JSON.toJSONString(requestMap));
        JSONObject jsonObject = JSONObject.parseObject(response);
        JSONArray objectList = jsonObject.getJSONArray("objectList");
        return objectList.getJSONObject(0).getString("md5");
    }
}
