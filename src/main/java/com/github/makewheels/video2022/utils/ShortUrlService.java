package com.github.makewheels.video2022.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShortUrlService {
    @Value("${short-url-service}")
    private String shortUrlService;

    public String getShortUrl(String fullUrl) {
        JSONObject body = new JSONObject();
        body.put("fullUrl", fullUrl);
        body.put("sign", "DuouXm25hwFWVbUmyw3a");
        String response = HttpUtil.post(shortUrlService, body.toJSONString());
        log.info("getShortUrl: body = {}, response = {}",
                JSON.toJSONString(body), JSON.toJSONString(response));
        return response;
    }
}
